;;; init.el --- Emacs config for artigo-revinfor (LaTeX + BibTeX)
;;
;; Usage:
;;   emacs -q -l /path/to/ides-configuration/emacs/init.el
;;
;; Or define a shell alias in your shell rc:
;;   alias emacs-revinfor='emacs -q -l ~/path/to/artigo-revinfor/ides-configuration/emacs/init.el'


;;; ---------------------------------------------------------------
;; 0a. Native compiler — silence false-positive warnings
;;     When packages are compiled to native code for the first time,
;;     the async compiler processes each file in isolation and cannot
;;     resolve cross-file forward references (e.g. modus-themes-theme,
;;     pdf-view-goto-page, ebib--update-buffers).  These warnings do
;;     not affect runtime behavior.  Setting this variable to 'silent
;;     keeps native compilation active while suppressing the noise.
;;; ---------------------------------------------------------------

(setq native-comp-async-report-warnings-errors 'silent)


;;; ---------------------------------------------------------------
;; 0b. Package bootstrap (straight.el + use-package)
;;     All packages land in a local .emacs-revinfor/ cache beside
;;     this file, keeping the system Emacs config untouched.
;;; ---------------------------------------------------------------

(defconst revinfor/config-dir
  (file-name-directory (or load-file-name buffer-file-name))
  "Directory that contains this init.el.")

(defconst revinfor/cache-dir
  (expand-file-name ".emacs-revinfor/" revinfor/config-dir)
  "Local package cache — keeps the system ~/.emacs.d clean.")

;; Redirect all Emacs ephemeral files into the local cache
(setq user-emacs-directory revinfor/cache-dir)
(setq package-user-dir (expand-file-name "elpa/" revinfor/cache-dir))

;; Bootstrap straight.el
(defvar bootstrap-version)
(let ((bootstrap-file
       (expand-file-name "straight/repos/straight.el/bootstrap.el"
                         revinfor/cache-dir))
      (bootstrap-version 7))
  (unless (file-exists-p bootstrap-file)
    (with-current-buffer
        (url-retrieve-synchronously
         "https://raw.githubusercontent.com/radian-software/straight.el/develop/install.el"
         'silent 'inhibit-cookies)
      (goto-char (point-max))
      (eval-print-last-sexp)))
  (load bootstrap-file nil 'nomessage))

(straight-use-package 'use-package)
(setq straight-use-package-by-default t)
(setq use-package-always-ensure t)


;;; ---------------------------------------------------------------
;; 1. Docker tool wrappers
;;    bin/ contains docker-latexmk and docker-chktex, which proxy
;;    the real binaries into the texlive container defined in the
;;    Makefile.  No local TeX installation is required.
;;; ---------------------------------------------------------------

(let ((bin-dir (expand-file-name "bin/" revinfor/config-dir)))
  (add-to-list 'exec-path bin-dir)
  (setenv "PATH" (concat bin-dir ":" (getenv "PATH"))))


;;; ---------------------------------------------------------------
;; 2. Core UI / UX
;;; ---------------------------------------------------------------

(setq inhibit-startup-screen t
      initial-scratch-message nil
      ring-bell-function 'ignore)

(menu-bar-mode   1)    ; on, to host the "Revinfor" build menu (section 16)
(tool-bar-mode   -1)
(scroll-bar-mode -1)
(column-number-mode 1)
(global-display-line-numbers-mode 1)
(show-paren-mode 1)

;; Sensible defaults
(setq-default indent-tabs-mode nil
              fill-column 80)
(setq sentence-end-double-space nil
      require-final-newline t)

;; Keep backup and auto-save files out of the project tree
(setq backup-directory-alist
      `(("." . ,(expand-file-name "backups/" revinfor/cache-dir)))
      auto-save-file-name-transforms
      `((".*" ,(expand-file-name "auto-save/" revinfor/cache-dir) t)))

;; Unlike backup-directory-alist, Emacs does not auto-create the
;; auto-save directory, so it must be created explicitly here.
(make-directory (expand-file-name "auto-save/" revinfor/cache-dir) t)


;;; ---------------------------------------------------------------
;; 3. Fonts — buffer and UI text only
;;    Tries a list of fonts commonly unpacked by hand into
;;    ~/.local/share/fonts, falling back to DejaVu Sans Mono, which
;;    ships with Fedora.
;;
;;    None of these are packaged in Fedora's repositories (only
;;    cascadia-*-nf-fonts are), so each has to be unpacked by hand from
;;    its nerd-fonts release zip; on a machine where that was never
;;    done the list simply falls through to DejaVu.
;;
;;    Whichever one wins is purely cosmetic and has no bearing on
;;    treemacs' icons: those are drawn in a separate symbols-only font,
;;    installed independently — see section 14.
;;; ---------------------------------------------------------------

(defun revinfor/set-font ()
  "Set the default and fixed-pitch face to the first available font."
  (let ((font (seq-find (lambda (f) (member f (font-family-list)))
                         '("FiraCode Nerd Font Mono"
                           "JetBrainsMono Nerd Font Mono"
                           "Hack Nerd Font Mono"
                           "CaskaydiaCove Nerd Font Mono"
                           ;; Fedora's cascadia-mono-nf-fonts ships
                           ;; Microsoft's own CascadiaMonoNF-*.otf, a
                           ;; different build from the nerd-fonts
                           ;; project's "Caskaydia*" patch above, and it
                           ;; registers under its own family name.
                           "Cascadia Mono NF"
                           "DejaVu Sans Mono"))))
    (when font
      (set-face-attribute 'default nil :family font :height 110)
      (set-face-attribute 'fixed-pitch nil :family font :height 110))))

(add-hook 'after-init-hook #'revinfor/set-font)


;;; ---------------------------------------------------------------
;; 4. Theme
;;; ---------------------------------------------------------------

(use-package modus-themes
  :config
  (modus-themes-load-theme 'modus-operandi))   ; light theme, easy on the eyes
                                                ; swap for 'modus-vivendi for dark


;;; ---------------------------------------------------------------
;; 5. Completion framework (Vertico + Orderless + Marginalia)
;;; ---------------------------------------------------------------

(use-package vertico
  :init (vertico-mode 1))

(use-package orderless
  :custom
  (completion-styles '(orderless basic))
  (completion-category-overrides '((file (styles basic partial-completion)))))

(use-package marginalia
  :init (marginalia-mode 1))


;;; ---------------------------------------------------------------
;; 5b. In-buffer completion popup (Corfu + Cape)
;;
;;     Section 5 above only covers the *minibuffer*.  This section is
;;     what makes completion behave like an IDE: a popup that appears
;;     at point while you type, with no keystroke to trigger it.
;;
;;     Corfu is only the front-end.  It renders whatever Emacs'
;;     standard `completion-at-point-functions' offers, and AUCTeX 14
;;     already fills that hook in every LaTeX buffer — macros and
;;     environment names via `TeX--completion-at-point', macro
;;     arguments (package names, class names, ...) via
;;     `LaTeX--arguments-completion-at-point'.  So Corfu alone is
;;     enough to surface everything C-M-i used to hide behind a
;;     keystroke.  Cape then adds the source AUCTeX has no reason to
;;     provide: completion of ordinary words, which is what actually
;;     helps while writing the prose of an article.
;;; ---------------------------------------------------------------

(use-package corfu
  :init
  (global-corfu-mode 1)
  :custom
  (corfu-cycle t)
  (corfu-quit-no-match t)      ; get out of the way as soon as nothing matches
  (corfu-on-exact-match nil)   ; typing a complete word must not self-expand
  ;; The first candidate is highlighted straight away, so accepting it
  ;; is a single TAB — the behaviour every IDE has.  This is only safe
  ;; because RET is unbound below: in a prose-heavy LaTeX buffer the
  ;; popup is on screen most of the time, and were RET still bound to
  ;; `corfu-insert', every Enter would silently complete a word.
  (corfu-preselect 'first)
  ;; Upstream's default ('insert) writes the highlighted candidate into
  ;; the buffer as a preview.  Turned off here: the buffer must not
  ;; change until TAB is pressed, otherwise the text flickers under the
  ;; cursor while typing and flyspell keeps re-checking half-words.
  (corfu-preview-current nil)
  :bind
  (:map corfu-map
        ("RET"    . nil)       ; Enter stays Enter, popup or no popup
        ([return] . nil)
        ("TAB"    . corfu-insert)
        ([tab]    . corfu-insert)
        ("C-n"    . corfu-next)
        ("C-p"    . corfu-previous)
        ("C-g"    . corfu-quit))
  :config
  ;; Since Corfu 2.8 the auto-completion engine is a separate file, and
  ;; `corfu-mode' only pulls it in when `corfu-auto' is already non-nil
  ;; (see the (when corfu-auto (require 'corfu-auto)) in corfu-mode).
  ;; Requiring it here is what brings the three variables below into
  ;; existence — setting them beforehand would just be setting symbols
  ;; nothing reads yet.
  (require 'corfu-auto)
  (setq corfu-auto t                ; the entire point: no C-M-i needed
        ;; Upstream warns that a short delay or a prefix below 2 puts
        ;; real load on Emacs, since every keystroke re-runs the Capf.
        ;; These stay at sane values because the trigger characters
        ;; below already give instant feedback where it matters.
        corfu-auto-delay 0.2
        corfu-auto-prefix 3
        ;; A trigger character bypasses `corfu-auto-prefix' completely:
        ;; the popup opens on the very next keystroke.  "\" and "{" are
        ;; precisely the two spots where a LaTeX author wants the list
        ;; immediately — starting a macro, and opening a macro argument
        ;; such as the package name in \usepackage{}.  Prose keeps the
        ;; calmer 3-character behaviour.
        corfu-auto-trigger "\\{")

  ;; Side panel showing the docstring of the highlighted candidate —
  ;; for LaTeX that is AUCTeX's own help text for the macro.  Lives in
  ;; corfu's extensions/ subdirectory, which straight.el installs too.
  (require 'corfu-popupinfo)
  (corfu-popupinfo-mode 1)
  (setq corfu-popupinfo-delay '(0.5 . 0.3)))

;; NOTE: Corfu draws its popup in a child frame.  On this Emacs (30.2)
;; child frames are graphical-only, so under `emacs -nw' the popup
;; silently never appears — the fix there is the corfu-terminal
;; package, left out on purpose since this config is launched
;; graphically.  Emacs 31 gains TTY child frames and drops the need.

(use-package cape
  ;; Loaded eagerly on purpose.  The options below are plain defcustoms
  ;; that spring into existence only when cape.el itself is loaded, so
  ;; deferring the package (`:after', `:defer') would leave them unset
  ;; and silently fall back to the defaults.
  :demand t
  :init
  (defun revinfor/latex-capf-setup ()
    "Add prose and file-path completion sources to the current LaTeX buffer.
Appended with a high depth so they sit *after* AUCTeX's own entries:
a \"\\\" prefix must always complete as a macro, never as some word
picked up from the running text."
    (add-hook 'completion-at-point-functions #'cape-file    90 t)
    (add-hook 'completion-at-point-functions #'cape-dabbrev 95 t))
  (add-hook 'LaTeX-mode-hook #'revinfor/latex-capf-setup)
  :custom
  ;; Long Portuguese terms and author surnames get retyped constantly
  ;; across an article's .tex files, so candidates are gathered from
  ;; every open LaTeX buffer, not just the current one.  How *early*
  ;; those candidates are offered is not set here — that is Corfu's
  ;; `corfu-auto-prefix' above.
  (cape-dabbrev-buffer-function #'cape-same-mode-buffers)
  ;; Kept on so `cape-file' stays quiet in prose: with no "file:"
  ;; prefix typed, it only offers itself once the text at point holds a
  ;; "/" whose parent directory actually exists.  That is exactly the
  ;; \includegraphics{imagens/...} case, and never an ordinary word.
  (cape-file-directory-must-exist t))


;;; ---------------------------------------------------------------
;; 5c. Revinfor build-flavor tables — shared between the AUCTeX
;;     commands (section 6) and the pdf-tools completion hook
;;     (section 7). Each flavor's docker-latexmk run gets its own
;;     -jobname so the two builds don't overwrite the same main.pdf.
;;; ---------------------------------------------------------------

(defconst revinfor/flavor-image-alist
  '(("abnt"     . "artigo-revinfor-latex:latest")
    ("overleaf" . "artigo-revinfor-overleaf:latest"))
  "Maps each Makefile FLAVOR to its Docker image name.")

(defconst revinfor/flavor-tex-command-alist
  '(("abnt"     . "docker-latexmk-abnt")
    ("overleaf" . "docker-latexmk-overleaf"))
  "Maps each Makefile FLAVOR to its TeX-command-list entry name.")

(defconst revinfor/flavor-pdf-alist
  '(("abnt"     . "main-abnt.pdf")
    ("overleaf" . "main-overleaf.pdf"))
  "Maps each Makefile FLAVOR to the PDF filename its -jobname flag
produces (see the docker-latexmk-* entries in section 6). AUCTeX's
own `TeX-active-master' has no notion of a custom jobname — it always
resolves to the plain master name — so this table is what the
Revinfor build/view commands and the completion hook use instead.")

(defvar revinfor/current-build-flavor nil
  "Flavor of the most recently started Revinfor docker-latexmk build.
Read by the completion-message hook in section 7, since AUCTeX's own
hook argument doesn't account for the custom jobname.")


;;; ---------------------------------------------------------------
;; 6. AUCTeX — LaTeX editing (all compilation via Docker)
;;; ---------------------------------------------------------------

;; :straight auctex installs the package; :package-name tex tells
;; use-package to (require 'tex), where TeX-command-list is defined.
;; Using (use-package auctex ...) would run :config before tex.el loads,
;; leaving TeX-command-list void.
(use-package tex
  :straight auctex
  :mode ("\\.tex\\'" . LaTeX-mode)
  :hook
  (LaTeX-mode . visual-line-mode)
  (LaTeX-mode . flyspell-mode)
  (LaTeX-mode . LaTeX-math-mode)
  (LaTeX-mode . turn-on-reftex)
  (LaTeX-mode . flymake-mode)     ; real-time syntax via docker-chktex
  :custom
  (TeX-auto-save t)
  (TeX-parse-self t)
  (TeX-save-query nil)
  (TeX-source-correlate-mode t)
  (TeX-source-correlate-method 'synctex)
  (TeX-engine 'default)             ; 'default = pdflatex in modern AUCTeX
  (TeX-chktex-program "docker-chktex")
  :config
  ;; Two TeX-command-list entries mirror the Makefile's FLAVOR=abnt|overleaf
  ;; choice (see section 1's docker-latexmk wrapper): both call the same
  ;; script, but the overleaf one overrides REVINFOR_LATEX_IMAGE so it runs
  ;; against the Overleaf-compatible container instead. Each also sets its
  ;; own -jobname (see revinfor/flavor-pdf-alist, section 5c) so an ABNT
  ;; build and an Overleaf build never overwrite each other's PDF.
  (add-to-list 'TeX-command-list
               '("docker-latexmk-abnt"
                 "docker-latexmk -pdf -interaction=nonstopmode -jobname=main-abnt %t"
                 TeX-run-command nil t
                 :help "Run latexmk inside the ABNT texlive Docker container"))
  (add-to-list 'TeX-command-list
               '("docker-latexmk-overleaf"
                 "REVINFOR_LATEX_IMAGE=artigo-revinfor-overleaf:latest docker-latexmk -pdf -interaction=nonstopmode -jobname=main-overleaf %t"
                 TeX-run-command nil t
                 :help "Run latexmk inside the Overleaf-compatible texlive Docker container"))
  (setq TeX-command-default "docker-latexmk-abnt")
  (setq-default TeX-master nil)   ; prompts once, then caches in .dir-locals

  ;; "Revinfor" menu — one-click Build/View Final PDF entries per flavor,
  ;; so the docker-latexmk pipeline above doesn't require going through
  ;; C-c C-c and selecting the command by name every time.
  (defun revinfor/run-docker-latexmk (flavor)
    "Kick off the docker-latexmk TeX-command for FLAVOR with a heads-up message.
Each run starts a fresh container and can do several latexmk passes
(pdflatex/bibtex), so AUCTeX's single \"Running...\" echo can otherwise
read as a hang. `sit-for' holds this message on screen briefly so it
isn't instantly overwritten by that echo."
    (setq revinfor/current-build-flavor flavor)
    (message "Revinfor: compiling via docker-latexmk (%s) → %s — a full build (container start + multiple latexmk passes) can take up to a minute; it is not stuck. Press C-c C-l to watch live output."
             flavor (alist-get flavor revinfor/flavor-pdf-alist nil nil #'string=))
    (sit-for 1.5)
    (TeX-command (alist-get flavor revinfor/flavor-tex-command-alist nil nil #'string=)
                 #'TeX-master-file (if TeX-save-query 0 1)))

  (defun revinfor/build-pdf (flavor)
    "Compile the current article's master file via docker-latexmk.
FLAVOR is \"abnt\" or \"overleaf\" (see the Makefile). Builds that
flavor's Docker image first if it isn't present locally yet."
    (let ((image (alist-get flavor revinfor/flavor-image-alist nil nil #'string=)))
      (if (zerop (call-process "docker" nil nil nil "image" "inspect" image))
          (revinfor/run-docker-latexmk flavor)
        (let* ((tex-buffer (current-buffer))
               (default-directory (expand-file-name "../../" revinfor/config-dir))
               (buf (get-buffer-create (format "*Revinfor Docker Build (%s)*" flavor))))
          (message "Revinfor: %s image not found — building it now (first run, a few minutes)..." image)
          (pop-to-buffer buf)
          (make-process
           :name (format "revinfor-docker-build-%s" flavor)
           :buffer buf
           :command (list "make" "docker-build" (format "FLAVOR=%s" flavor))
           :sentinel
           (lambda (proc _event)
             (when (memq (process-status proc) '(exit signal))
               (if (zerop (process-exit-status proc))
                   (if (buffer-live-p tex-buffer)
                       (with-current-buffer tex-buffer
                         (message "Revinfor: Docker image built — starting PDF build.")
                         (revinfor/run-docker-latexmk flavor))
                     (message "Revinfor: Docker image built — re-run Build Final PDF."))
                 (message "Revinfor: docker-build failed — see %s buffer" (buffer-name buf))))))))))

  (defun revinfor/build-pdf-abnt ()
    "Build the final PDF using the ABNT flavor Docker image."
    (interactive)
    (revinfor/build-pdf "abnt"))

  (defun revinfor/build-pdf-overleaf ()
    "Build the final PDF using the Overleaf-compatible flavor Docker image."
    (interactive)
    (revinfor/build-pdf "overleaf"))

  (defun revinfor/view-pdf (flavor)
    "Open FLAVOR's built PDF for the current article in pdf-view-mode.
Bypasses AUCTeX's own C-c C-v / TeX-view, which always looks for the
plain master name and would never find a jobname'd main-<flavor>.pdf."
    (let* ((pdf-name (alist-get flavor revinfor/flavor-pdf-alist nil nil #'string=))
           (pdf-file (expand-file-name pdf-name (TeX-master-directory))))
      (if (file-exists-p pdf-file)
          (find-file-other-window pdf-file)
        (user-error "Revinfor: %s not found yet — build it first" pdf-name))))

  (defun revinfor/view-pdf-abnt ()
    "View the current article's ABNT flavor PDF."
    (interactive)
    (revinfor/view-pdf "abnt"))

  (defun revinfor/view-pdf-overleaf ()
    "View the current article's Overleaf flavor PDF."
    (interactive)
    (revinfor/view-pdf "overleaf"))

  ;; LaTeX-mode-map lives in latex.el, not tex.el, and AUCTeX only
  ;; loads latex.el when a .tex file is actually opened — so defining
  ;; the menu here directly would fail with a void-variable error.
  (with-eval-after-load 'latex
    (easy-menu-define revinfor/latex-menu LaTeX-mode-map "Revinfor build commands."
      '("Revinfor"
        ["Build Final PDF (ABNT)" revinfor/build-pdf-abnt t]
        ["View PDF (ABNT)" revinfor/view-pdf-abnt t]
        "--"
        ["Build Final PDF (Overleaf)" revinfor/build-pdf-overleaf t]
        ["View PDF (Overleaf)" revinfor/view-pdf-overleaf t]))))


;;; ---------------------------------------------------------------
;; 7. PDF viewer — pdf-tools (in-Emacs viewer with SyncTeX support)
;;; ---------------------------------------------------------------

(use-package pdf-tools
  :magic ("%PDF" . pdf-view-mode)
  :config
  (pdf-tools-install :no-query)
  (setq pdf-view-display-size 'fit-width)
  ;; Wire SyncTeX: C-c C-v in LaTeX-mode jumps to the matching PDF spot
  (setq TeX-view-program-selection '((output-pdf "PDF Tools")))
  (setq TeX-view-program-list
        '(("PDF Tools" TeX-pdf-tools-sync-view)))
  ;; Bookends the "not stuck" message from revinfor/run-docker-latexmk
  ;; (section 6) with a clear end-of-build signal, and reverts an
  ;; already-open pdf-view buffer on the just-built flavor's PDF.
  ;; AUCTeX's stock TeX-revert-document-buffer isn't used here: its
  ;; hook argument is `TeX-active-master', which stays the plain
  ;; master name and never resolves to a -jobname'd main-<flavor>.pdf
  ;; (see revinfor/flavor-pdf-alist, section 5b), so it would always
  ;; look for a main.pdf buffer that these builds never produce.
  (add-hook 'TeX-after-compilation-finished-functions
            (lambda (_file)
              (let* ((flavor revinfor/current-build-flavor)
                     (pdf-name (and flavor (alist-get flavor revinfor/flavor-pdf-alist
                                                       nil nil #'string=))))
                (when pdf-name
                  (let* ((pdf-file (expand-file-name pdf-name (TeX-master-directory)))
                         (buf (get-file-buffer pdf-file)))
                    (when buf (with-current-buffer buf (revert-buffer t t t)))))
                (message "Revinfor: PDF build finished (%s)."
                         (or pdf-name (file-name-nondirectory _file)))))))


;;; ---------------------------------------------------------------
;; 8. BibTeX / ebib — bibliography manager
;;    ebib is the closest Linux equivalent to BibDesk.
;;; ---------------------------------------------------------------

(use-package ebib
  :bind
  ("C-c e" . ebib)               ; open ebib from anywhere
  :custom
  ;; Point ebib at the project's bibliography files
  (ebib-bib-search-dirs
   (list (expand-file-name "../../" revinfor/config-dir)           ; repo root
         (expand-file-name "../../common-shared/bib/" revinfor/config-dir)
         (expand-file-name "../../artigo_modelo_revista_infor/" revinfor/config-dir)
         (expand-file-name "../../artigo_curso_defectologia_vigostky/" revinfor/config-dir)))
  (ebib-preload-bib-files
   '("artigo_modelo_revista_infor/references.bib"
     "artigo_curso_defectologia_vigostky/references.bib"
     "common-shared/bib/abntex2-modelo-references.bib"))
  (ebib-index-default-sort '("Author" . ascend))
  (ebib-use-timestamp t)
  :config
  ;; Insert ABNT-style citation with \citeonline{key} via C-c C-e c
  (defun revinfor/ebib-insert-citeonline ()
    "Insert \\citeonline{KEY} at point using the current ebib entry."
    (interactive)
    (ebib-push-bibtex-key)
    (save-excursion
      (search-backward "{")
      (insert "\\citeonline"))))


;;; ---------------------------------------------------------------
;; 9. RefTeX — cross-references, citations, labels
;;    Works alongside AUCTeX; aware of abntex2 cite commands.
;;; ---------------------------------------------------------------

(use-package reftex
  :straight nil                  ; built into Emacs
  :after tex
  :custom
  (reftex-plug-into-AUCTeX t)
  ;; Teach RefTeX about the ABNT citation commands used in this project
  (reftex-cite-format
   '((?\C-m . "\\cite{%l}")
     (?o    . "\\citeonline{%l}")
     (?a    . "\\citeauthor{%l}")
     (?y    . "\\citeyear{%l}")
     (?f    . "\\citefullauthor{%l}")))
  (reftex-bibliography-commands '("bibliography" "nobibliography" "addbibresource")))


;;; ---------------------------------------------------------------
;; 10. Flyspell — spell checking (Brazilian Portuguese)
;;    hunspell is the only tool that still needs a local install;
;;    it checks natural language, not LaTeX, so Docker is not useful here.
;;    Install: sudo dnf install hunspell hunspell-pt-BR
;;; ---------------------------------------------------------------

(use-package flyspell
  :straight nil
  :hook (LaTeX-mode . flyspell-mode)
  :custom
  (ispell-program-name "hunspell")
  (ispell-dictionary "pt_BR")   ; hunspell-pt-BR on Fedora registers as "pt_BR"
                                 ; switch with M-x ispell-change-dictionary
  :config
  ;; Flyspell only marks a word when point moves onto it (its checking is
  ;; driven by post-command-hook, not a scan of the whole buffer) — text
  ;; already on disk stays unmarked until you happen to visit each word.
  ;; Force a full check right when the mode turns on so every misspelling
  ;; is already visible while just scrolling, without touching point.
  (add-hook 'flyspell-mode-hook
            (lambda ()
              (when flyspell-mode
                (flyspell-buffer)))))


;;; ---------------------------------------------------------------
;; 11. Org-mode tweaks (for README.org / PROJECT_ANALYSIS.org)
;;; ---------------------------------------------------------------

(use-package org
  :straight nil
  :mode ("\\.org\\'" . org-mode)
  :custom
  (org-startup-folded 'content)
  (org-hide-leading-stars t)
  (org-src-fontify-natively t)
  (org-src-tab-acts-natively t))


;;; ---------------------------------------------------------------
;; 12. Project navigation — project.el pointing at the repo root
;;; ---------------------------------------------------------------

(use-package project
  :straight nil
  :config
  ;; Register the repo root directly as a Git project.
  ;; project-remember-projects-under searches *inside* a directory for
  ;; sub-projects and won't register the directory itself.
  (let ((root (expand-file-name "../../" revinfor/config-dir)))
    (when (file-directory-p (expand-file-name ".git" root))
      (project-remember-project `(vc Git ,root)))))


;;; ---------------------------------------------------------------
;; 13. Magit — Git porcelain
;;; ---------------------------------------------------------------

(use-package magit
  :bind
  ("C-x g" . magit-status))


;;; ---------------------------------------------------------------
;; 14. Treemacs — file/directory sidebar
;;; ---------------------------------------------------------------

(use-package treemacs
  :bind
  (("<f8>"    . treemacs-select-window)  ; C-c C-o was requested, but AUCTeX
                                          ; (TeX-fold prefix) and org-mode
                                          ; (org-open-at-point) already claim
                                          ; it in .tex/.org buffers, so it
                                          ; would silently do nothing there.
   ("M-0"     . treemacs-select-window)
   ("C-x t t" . treemacs)
   ("C-x t d" . treemacs-select-directory)
   ("C-x t B" . treemacs-bookmark)
   ("C-x t C-t" . treemacs-find-file)
   ("C-x t M-t" . treemacs-find-tag))
  :custom
  (treemacs-width 35)
  (treemacs-is-never-other-window t)
  (treemacs-sorting 'alphabetic-case-insensitive-asc)
  :config
  (treemacs-follow-mode t)
  (treemacs-filewatch-mode t)
  (treemacs-fringe-indicator-mode 'always)
  (treemacs-git-mode 'deferred))

(use-package treemacs-nerd-icons
  :after treemacs
  :config
  ;; Treemacs' icons are NOT drawn in the editing font from section 3.
  ;; nerd-icons renders every glyph in `nerd-icons-font-family', which
  ;; defaults to "Symbols Nerd Font Mono" — a symbols-only font shipped
  ;; separately from any patched programming font, and absent from
  ;; Fedora's repositories.  Without it the whole sidebar degrades to
  ;; empty boxes, no matter which Nerd Font the buffer text uses; and
  ;; conversely it is enough on its own, even with plain DejaVu text.
  ;;
  ;; Install it once with M-x nerd-icons-install-fonts, which downloads
  ;; NFM.ttf into ~/.local/share/fonts and refreshes the font cache.
  ;; Until that exists, stay on treemacs' built-in theme rather than
  ;; filling the sidebar with tofu.
  (cond
   ;; Without a graphical display `find-font' has nothing to query, and
   ;; whether the glyphs show up is down to the terminal's own font
   ;; rather than to Emacs.  Load the theme and let the terminal decide.
   ((not (display-graphic-p))
    (treemacs-load-theme "nerd-icons"))
   ((find-font (font-spec :family nerd-icons-font-family))
    (treemacs-load-theme "nerd-icons"))
   (t
    (message "Treemacs: font %S not installed — run M-x nerd-icons-install-fonts to get icons"
             nerd-icons-font-family))))

;; Open treemacs automatically at startup, pointed at the repo root.
;; `treemacs-add-and-display-current-project-exclusively' resolves the
;; project via project.el (registered in section 12) without ever
;; prompting — unlike plain `treemacs', which asks for a root path
;; interactively the first time the workspace is empty.
;; default-directory at launch is the invoking shell's cwd, not the repo,
;; so it's bound locally just for this call.
(add-hook 'emacs-startup-hook
          (lambda ()
            (let ((default-directory (expand-file-name "../../" revinfor/config-dir)))
              (treemacs-add-and-display-current-project-exclusively))))


;;; ---------------------------------------------------------------
;; 15. Useful keybindings summary
;;
;;  LaTeX editing (LaTeX-mode):
;;    Revinfor menu — Build/View Final PDF (ABNT) / (Overleaf), no prompt
;;                    outputs: main-abnt.pdf / main-overleaf.pdf
;;    C-c C-c       — compile via docker-latexmk (pick abnt/overleaf; default: abnt)
;;    C-c C-v       — NOT wired for these: AUCTeX's view always looks for the
;;                    plain master name, not a -jobname'd file. Use the
;;                    Revinfor menu's View PDF items instead.
;;    C-c C-e       — insert environment
;;    C-c C-s       — insert section
;;    C-c [         — insert \cite via RefTeX   (also C-c C-e o for \citeonline)
;;    C-c (         — insert \label via RefTeX
;;    C-c )         — insert \ref   via RefTeX
;;    C-c e         — open ebib bibliography manager
;;    (flymake)     — docker-chktex runs automatically; errors shown inline
;;
;;  Autocomplete popup (Corfu — appears by itself while typing):
;;    TAB           — accept the highlighted candidate
;;    C-n / C-p     — next / previous candidate (arrow keys work too)
;;    RET           — deliberately NOT completion; inserts a newline
;;    C-g           — dismiss the popup
;;    C-M-i         — force the popup open before the auto-delay elapses
;;    M-t           — toggle the documentation panel for the candidate
;;    M-PgDn/M-PgUp — scroll that documentation panel
;;    Completes: \macros, \begin{env}, macro arguments (AUCTeX), plus
;;    words from open buffers and file paths (Cape).
;;
;;  PDF viewer (pdf-view-mode):
;;    C-c C-g       — SyncTeX backward search (jump to .tex source)
;;    n / p         — next / previous page
;;    + / -         — zoom in / out
;;    s w           — fit to window width
;;
;;  Treemacs (file/directory sidebar, opens automatically at startup):
;;    <f8> / M-0    — jump to the treemacs window
;;    C-x t t       — open/close treemacs
;;    C-x t d       — open treemacs for a chosen directory
;;    C-x t B       — treemacs bookmark
;;    C-x t C-t     — find current file in treemacs
;;
;;  Magit (Git porcelain):
;;    C-x g         — open magit-status
;;; ---------------------------------------------------------------

(provide 'init)
;;; init.el ends here
