# ==============================================================================
#  Dockerfile para Emacs em Modo Terminal (não-X)
#
#  Inclui:
#  - Emacs (sem dependências gráficas)
#  - Suporte completo para LaTeX (Tex Live)
#  - Pandoc para conversão de documentos
#  - Ferramentas para manipulação de imagens (ImageMagick) e PDF (Poppler)
# ==============================================================================

# Use uma imagem base leve e estável do Debian. "bookworm" é a versão estável atual.
FROM debian:bookworm-slim

# Evita que os instaladores peçam inputs interativos durante o build
ARG DEBIAN_FRONTEND=noninteractive

# Instala todas as dependências necessárias em um único passo para otimizar as camadas do Docker
RUN apt-get update && apt-get install -y --no-install-recommends \
    # 1. O Emacs principal em modo "no-x" (sem interface gráfica)
    emacs-nox \
    \
    # 2. Suporte a LaTeX
    # Uma seleção de pacotes que cobre a maioria dos casos de uso sem instalar o gigantesco "texlive-full"
    texlive-latex-base \
    texlive-latex-recommended \
    texlive-latex-extra \
    texlive-fonts-recommended \
    lmodern \
    biber \
    texlive-science \
    \
    # 3. Pandoc para conversão de arquivos (NOVO)
    # Ferramenta essencial para converter entre Markdown, Org, LaTeX, HTML, etc.
    pandoc \
    \
    # 4. Suporte a Imagens (para processamento, não visualização)
    # Usado pelo Org Mode para exportar imagens
    imagemagick \
    \
    # 5. Suporte a PDF (ferramentas de linha de comando)
    # Permite extrair texto e informações de PDFs (ex: pdftotext)
    poppler-utils \
    \
    # 6. Utilitários essenciais para desenvolvimento e gerenciamento
    git \
    make \
    locales \
    \
    # Limpa o cache do apt para reduzir o tamanho final da imagem
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/*

# Configura o Locale para evitar problemas com codificação de caracteres (UTF-8)
RUN sed -i -e 's/# pt_BR.UTF-8 UTF-8/pt_BR.UTF-8 UTF-8/' /etc/locale.gen && \
    sed -i -e 's/# en_US.UTF-8 UTF-8/en_US.UTF-8 UTF-8/' /etc/locale.gen && \
    dpkg-reconfigure --frontend=noninteractive locales

# Define o locale padrão do ambiente
ENV LANG pt_BR.UTF-8
ENV LANGUAGE pt_BR:pt
ENV LC_ALL pt_BR.UTF-8

# Cria um usuário não-root para rodar o Emacs. É uma boa prática de segurança.
RUN useradd -ms /bin/bash -d /home/emacsuser emacsuser
USER emacsuser

# Define o diretório de trabalho padrão dentro do container
WORKDIR /home/emacsuser

# Cria um diretório de trabalho e o define como um volume.
# Isso permite que você monte facilmente seus arquivos locais neste diretório.
RUN mkdir /home/emacsuser/work
VOLUME /home/emacsuser/work

# Define o comando padrão para iniciar o container.
# Inicia o Emacs em modo terminal ("no-window").
CMD ["emacs", "-nw"]
