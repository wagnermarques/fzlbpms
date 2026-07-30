# Aula: Certificados e HTTPS local com containers

> Material baseado na implementação real deste repositório (fzlbpms), feita em
> 2026-07-28. Todos os arquivos citados existem aqui e podem ser abertos ao
> lado desta aula. Caso de uso concreto: fazer o SSO Keycloak + Moodle +
> Flowable funcionar na máquina de desenvolvimento, já que o Moodle **exige**
> HTTPS nos endpoints OAuth2.

---

## 1. O problema que queremos resolver

Em produção, HTTPS "simplesmente funciona": você tem um domínio público
(`fzlbpms.com.br`), uma CA pública (Let's Encrypt, Cloudflare) emite um
certificado para ele, e todo navegador/sistema do planeta já confia nessa CA.

Em desenvolvimento local, nada disso existe:

1. **Não há domínio público.** CAs públicas não emitem certificado para
   `localhost` nem para nomes inventados como `fzlbpms.local`.
2. **Certificado self-signed gera aviso.** O navegador mostra "sua conexão
   não é particular", e bibliotecas de backend (curl, Guzzle, JVM) **recusam**
   a conexão — não é só estética.
3. **Alguns softwares exigem HTTPS.** O Moodle, por exemplo, valida que o
   issuer OAuth2 seja `https://` (classe `oauth2\issuer::validate_baseurl`,
   "Because we send Bearer tokens we must ensure SSL") e não tem flag para
   desligar isso. Ou você tem HTTPS local de verdade, ou não tem SSO local.

E com containers aparece um quarto problema, mais sutil:

4. **"localhost" é ambíguo.** Para o seu navegador, `localhost` é a sua
   máquina. Para um processo *dentro* de um container, `localhost` é o
   loopback **daquele container**. Se o Moodle (dentro do container
   `fzl-php8.3-fpm`) tenta chamar `https://localhost/auth/...`, ele bate na
   própria porta 443 dele mesmo — que não existe — e recebe *connection
   refused*. Vimos exatamente isso acontecer com o Flowable.

---

## 2. Conceito: a cadeia de confiança

Um certificado TLS não vale nada sozinho. O que faz o navegador aceitar é a
**cadeia**:

```
CA raiz (root CA)  ──assina──►  certificado do site (leaf)
      ▲
      │ precisa estar instalada no "trust store"
      │ de QUEM faz a requisição
```

O ponto-chave da aula inteira: **cada cliente tem o seu próprio trust
store**. Não existe um lugar único onde se instala a CA e pronto:

| Cliente                          | Trust store                                          |
|----------------------------------|------------------------------------------------------|
| curl / wget / PHP no host Fedora | `/etc/pki/ca-trust/` (rebuild com `update-ca-trust`) |
| Firefox / Chrome (Linux)         | banco NSS do usuário (`~/.pki/nssdb`)                |
| PHP/curl em container Debian     | `/etc/ssl/certs/` (rebuild com `update-ca-certificates`) |
| JVM (Flowable, Karaf...)         | `$JAVA_HOME/lib/security/cacerts` (via `keytool`)    |

Se a CA está no sistema mas não no NSS, o `curl` funciona e o Firefox
reclama. Se está no sistema do host mas não dentro do container, o navegador
funciona e o backend quebra. A maioria dos "HTTPS local não funciona" nasce
daí.

---

## 3. A ferramenta: mkcert

O [mkcert](https://github.com/FiloSottile/mkcert) resolve a parte da CA:

```bash
sudo dnf install mkcert nss-tools   # Fedora
mkcert -install                      # cria uma CA local e instala nos trust stores
mkcert -cert-file site.pem -key-file site-key.pem fzlbpms.local
```

- `mkcert -install` cria uma **CA só sua** (em `~/.local/share/mkcert/`) e a
  instala no trust store do sistema **e** no NSS dos navegadores. Por isso o
  pacote `nss-tools` (fornece o `certutil`).
- `mkcert <dominio>` emite um certificado leaf assinado por essa CA, com o
  SAN (`subjectAltName`) correto — que é o que os clientes modernos validam,
  não o CN.

A chave privada da CA nunca sai da sua máquina; ninguém mais confia nela.
Perfeito para dev, inaceitável para produção.

**Armadilha real (encontrada nesta implementação):** `mkcert -install` chama
`sudo` *internamente* para escrever no trust store do sistema. Rodando sob
Ansible não há terminal para digitar a senha e ele falha com
`sudo: um terminal é necessário para ler a senha`. A solução no nosso
playbook (`ansible/fzlbpms-setup.yml`) foi separar as duas metades:

```yaml
# metade do usuário: só o NSS dos navegadores (não precisa de sudo)
- command: mkcert -install
  environment: { TRUST_STORES: nss }
  become: false

# metade do root: o que o sudo interno do mkcert faria, feito pelo Ansible
- copy:
    src: "{{ caroot }}/rootCA.pem"
    dest: /etc/pki/ca-trust/source/anchors/mkcert-wgn-rootCA.pem
  become: true
- command: update-ca-trust
  become: true
```

Depois disso, um `mkcert -install` normal vira no-op (ele detecta que a CA
já está instalada e não pede sudo) — é isso que deixa
`bin/setup-local-https.sh` rodar tanto interativamente quanto sob Ansible.

---

## 4. Um hostname que funcione para todos

Escolhemos `fzlbpms.local` como nome canônico local. Ele precisa resolver
para o lugar certo a partir de **dois mundos diferentes**:

```
navegador (host) ── /etc/hosts ──────────► 127.0.0.1 ──► porta 443 publicada
                                                            │
containers ──── alias de rede Docker ──────────────────► fzl-nginx:443
```

1. **Para o navegador**: uma linha em `/etc/hosts` (tarefa `lineinfile` no
   playbook):
   ```
   127.0.0.1 fzlbpms.local
   ```
2. **Para os outros containers**: um *network alias* no serviço nginx do
   `docker-compose.yml` — o DNS embutido do Docker passa a resolver
   `fzlbpms.local` para o IP do container nginx dentro da rede:
   ```yaml
   fzl-nginx:
     networks:
       fzl-network:
         aliases:
           - fzlbpms.local
   ```

Resultado: a URL `https://fzlbpms.local/auth/realms/fzlbpms` é **a mesma** e
**válida** para o navegador, para o Moodle (PHP) e para o Flowable (JVM).
Isso elimina a ambiguidade do `localhost` (problema 4 da seção 1) — e é o
requisito para o Keycloak, cujo `KC_HOSTNAME` precisa ser um valor único e
consistente para todo mundo.

---

## 5. Terminação TLS no nginx

Só **um** lugar termina TLS: o nginx. Os containers de aplicação continuam
falando HTTP puro entre si atrás do proxy. Arquivo
`containers/fzl-nginx/nginx-conf.d/01-tls.conf`:

```nginx
server {
    listen 443 ssl;
    server_name fzlbpms.local;

    ssl_certificate     /etc/nginx/certs/fzlbpms.local.pem;
    ssl_certificate_key /etc/nginx/certs/fzlbpms.local-key.pem;

    include /etc/nginx/shared/app-server.conf;
}
```

Duas decisões de projeto valem destaque:

**a) Bloco compartilhado.** As rotas (`location`s de Moodle, Keycloak,
Flowable, SPA...) moraram sempre no server block de :80. Se copiássemos tudo
para o :443, os dois divergiriam com o tempo. Extraímos o corpo para
`nginx-shared/app-server.conf` e os dois blocos fazem `include` — uma fonte
de verdade só.

**b) Certificado fallback embutido na imagem.** Se o nginx aponta para um
arquivo de certificado que não existe, ele **não sobe** — e todo colega que
clonar o repo sem rodar o mkcert teria o stack quebrado. Por isso o
`Dockerfile` gera um self-signed no build:

```dockerfile
RUN apk add --no-cache openssl \
    && openssl req -x509 -newkey rsa:2048 -nodes -days 3650 \
        -keyout /etc/nginx/certs/fzlbpms.local-key.pem \
        -out    /etc/nginx/certs/fzlbpms.local.pem \
        -subj "/CN=fzlbpms.local"
```

E um script em `/docker-entrypoint.d/` (convenção da imagem oficial do
nginx: todo script ali roda antes do nginx iniciar) **sobrescreve** o
fallback com o certificado real do mkcert, se ele tiver sido montado:

```sh
# containers/fzl-nginx/docker-entrypoint.d/10-install-mkcert-certs.sh
if [ -f /run/mkcert-certs/fzlbpms.local.pem ]; then
    cp /run/mkcert-certs/fzlbpms.local.pem     /etc/nginx/certs/
    cp /run/mkcert-certs/fzlbpms.local-key.pem /etc/nginx/certs/
fi
```

Detalhe importante: o volume monta um **diretório**
(`./containers/fzl-nginx/certs:/run/mkcert-certs:ro`), não arquivos
individuais. Bind mount de arquivo que não existe faz o Docker criar um
*diretório* vazio com aquele nome e quebra tudo; bind mount de diretório
inexistente só cria um diretório vazio inofensivo. Sempre monte o diretório.

---

## 6. Distribuindo a CA para os backends

O navegador confia via NSS, o host via `update-ca-trust`. Faltam os
containers que fazem chamadas HTTPS *de dentro* (Moodle chama o token
endpoint do Keycloak; Flowable faz discovery OIDC no boot). Cada runtime tem
seu ritual:

### 6.1 Container Debian/PHP (`fzl-php8.3-fpm`)

O `update-ca-certificates` do Debian varre
`/usr/local/share/ca-certificates/**/*.crt` (extensão `.crt` obrigatória!) e
reconstrói o bundle `/etc/ssl/certs/ca-certificates.crt`, que PHP/curl usam.
Basta montar a CA lá e rodar o comando no entrypoint:

```yaml
volumes:
  - ./containers/fzl-php8.3-fpm/certs:/usr/local/share/ca-certificates/mkcert:ro
```
```bash
# docker-entrypoint.sh — no-op inofensivo se o diretório estiver vazio
update-ca-certificates
```

### 6.2 Container JVM (`fzl-flowable-ui`)

A JVM ignora o trust store do sistema operacional: ela usa o keystore
`$JAVA_HOME/lib/security/cacerts`. Dois obstáculos:

1. O container roda como usuário `flowable`, sem permissão de escrita no
   `cacerts` (dono root).
2. Não controlamos o Dockerfile (imagem de terceiro).

Solução: no entrypoint (sobrescrito via `docker-compose.yml`), copiar o
`cacerts` para um caminho gravável, importar a CA com `keytool` e apontar a
JVM para a cópia:

```sh
cp "$JAVA_HOME/lib/security/cacerts" /tmp/cacerts-with-mkcert
keytool -importcert -noprompt -trustcacerts \
    -alias mkcert-local-ca -file /run/mkcert-certs/mkcert-ca.pem \
    -keystore /tmp/cacerts-with-mkcert -storepass changeit
export JAVA_OPTS="$JAVA_OPTS -Djavax.net.ssl.trustStore=/tmp/cacerts-with-mkcert \
                             -Djavax.net.ssl.trustStorePassword=changeit"
exec /flowable-entrypoint.sh
```

**Armadilha de sintaxe:** script embutido no `docker-compose.yml` precisa de
`$$` no lugar de `$` (`$$JAVA_HOME`), senão o próprio Compose interpola a
variável (com o ambiente do *host*) antes de o container ver o script. O
aviso `The "JAVA_HOME" variable is not set` no `docker compose config` é o
sintoma.

---

## 7. Persistência: sobreviver ao rebuild

Princípios que aplicamos para que `docker compose build`/`up` do zero
continue funcionando:

1. **Certificados nunca vão para a imagem nem para o git.** São
   por-desenvolvedor (a CA de cada máquina é diferente). Ficam em
   `containers/*/certs/`, listados no `.gitignore`, com um `.gitkeep` para o
   diretório existir no clone.
2. **A instalação da confiança acontece no *entrypoint*, não no build.**
   Entrypoint roda a cada start e enxerga os bind mounts; build não. Assim,
   trocar o certificado exige só um `docker compose restart`, nunca rebuild.
   (Corolário: `docker compose up -d` **não** reinicia container cujo config
   não mudou — depois de regenerar certificados, use `restart`.)
3. **Tudo que exige root na máquina fica no playbook.** `ansible/
   fzlbpms-setup.yml` instala mkcert, escreve o `/etc/hosts`, instala a CA
   no sistema e chama `bin/setup-local-https.sh` — idempotente, roda quantas
   vezes quiser:
   ```bash
   ansible-playbook ansible/fzlbpms-setup.yml -i ansible/inventory.ini -K
   ```
4. **Degradação suave.** Sem mkcert, o stack sobe do mesmo jeito com o
   certificado fallback (navegador avisa, mas nada quebra silenciosamente) e
   os entrypoints apenas logam um aviso dizendo qual script rodar.

---

## 8. Efeitos colaterais reais (o que quebrou depois do HTTPS funcionar)

TLS válido não é o fim da história. Duas quebras que só apareceram *depois*
do handshake passar — e que valem como lição:

**a) Proteção anti-SSRF do Moodle.** O Moodle bloqueia por padrão requisições
curl para faixas de IP privadas, incluindo `172.16.0.0/12` — exatamente a
faixa da rede bridge do Docker onde `fzlbpms.local` resolve. O discovery
OAuth2 falhava com "The URL is blocked". Corrigimos em
`bin/moodle/configure-oauth2-keycloak.php` removendo só essa faixa da lista
`curlsecurityblockedhosts` (as demais proteções ficam).

**b) redirect_uri registrado no Keycloak.** O client OAuth2 no Keycloak
guarda a lista de `redirectUris` válidas. Nosso bootstrap (Camel) só *cria*
o client se não existir — nunca atualiza. Ao trocar de domínio, o client
ficou com as URIs antigas e o login morria com "Invalid parameter:
redirect_uri". O `bin/switch-domain.sh` agora reregistra as URIs dos três
clients via API admin do Keycloak a cada troca de domínio.

Moral: numa cadeia de SSO, o certificado é só a primeira camada. Depois dele
vêm as validações da *aplicação* (listas de hosts, redirect URIs, issuers
fixos) que também carregam hostname e precisam acompanhar a mudança.

---

## 9. Como verificar (kit de diagnóstico)

```bash
# Qual certificado o nginx está servindo? (issuer diz se é o mkcert ou o fallback)
echo | openssl s_client -connect fzlbpms.local:443 -servername fzlbpms.local \
    | openssl x509 -noout -issuer -subject -enddate

# O host confia? (ssl_verify:0 = ok; sem -k!)
curl -s -o /dev/null -w "%{http_code} ssl_verify:%{ssl_verify_result}\n" \
    https://fzlbpms.local/moodle/

# E de dentro de um container?
docker exec fzl-php8.3-fpm curl -s -o /dev/null \
    -w "%{http_code} ssl_verify:%{ssl_verify_result}\n" \
    https://fzlbpms.local/auth/realms/fzlbpms/.well-known/openid-configuration

# A cadeia fecha contra o bundle do container?
docker exec fzl-php8.3-fpm sh -c '
  echo | openssl s_client -connect fzl-nginx:443 -servername fzlbpms.local \
      2>/dev/null | openssl x509 > /tmp/leaf.pem
  openssl verify -CAfile /etc/ssl/certs/ca-certificates.crt /tmp/leaf.pem'

# A JVM do Flowable importou a CA?
docker logs fzl-flowable-ui 2>&1 | grep "Trusted the local mkcert CA"
```

Dica de leitura dos sintomas:

| Sintoma                                   | Provável causa                                    |
|-------------------------------------------|---------------------------------------------------|
| Navegador ok, backend falha TLS           | CA não instalada no trust store *do container*    |
| curl ok, Firefox reclama                  | CA no sistema mas não no NSS (faltou `nss-tools`) |
| `Could not resolve host`                  | Falta `/etc/hosts` (host) ou network alias (container) |
| `Connection refused` de dentro do container | URL usa `localhost` — loopback errado           |
| Issuer do certificado = fallback          | Container não foi *reiniciado* após gerar os certs |
| TLS ok mas "URL is blocked" / "invalid redirect_uri" | Validações da aplicação (seção 8)      |

---

## 10. Resumo em uma frase por camada

1. **CA**: mkcert cria uma CA local e a instala nos trust stores (sistema +
   navegador; playbook divide a parte que precisa de root).
2. **Nome**: um hostname único (`fzlbpms.local`) resolvido por `/etc/hosts`
   no host e por network alias nos containers.
3. **TLS**: terminado num único lugar (nginx :443), com config compartilhada
   com o :80 e fallback self-signed para nunca quebrar o `up`.
4. **Confiança nos backends**: a CA é montada em cada container e instalada
   no trust store *daquele runtime* no entrypoint (Debian:
   `update-ca-certificates`; JVM: cópia do `cacerts` + `keytool`).
5. **Aplicação**: hostnames gravados em configs (Moodle wwwroot, clients do
   Keycloak, issuer do Flowable) são atualizados pelo `bin/switch-domain.sh`.
