# Vistoria Arbórea – app Android

App offline para coletar dados de avaliação fitossanitária e de risco de árvores e gerar:
- o laudo em Word (.docx), no formato do laudo modelo de Almirante Tamandaré;
- a planilha de dados em Excel (.xlsx).

## Como gerar o APK pelo GitHub (sem instalar nada)

1. Crie uma conta gratuita em github.com, se ainda não tiver.
2. Clique em **New repository**, dê o nome `vistoria-arvores` e marque **Private**. Clique em **Create repository**.
3. Na página do repositório, clique em **uploading an existing file**.
4. Descompacte o arquivo `VistoriaArvores.zip` no computador e arraste **todo o conteúdo da pasta** (inclusive a pasta `.github`) para a página. Clique em **Commit changes**.
   - Se o navegador não mostrar a pasta `.github` (pastas que começam com ponto ficam ocultas), crie o arquivo manualmente: **Add file → Create new file**, nome `.github/workflows/build-apk.yml`, e cole o conteúdo do arquivo de mesmo nome.
5. Abra a aba **Actions**. O processo “Gerar APK” começa sozinho e leva cerca de 3 a 5 minutos.
6. Quando ficar verde, clique nele e baixe **VistoriaArborea-apk** em *Artifacts*. Descompacte para obter o `app-debug.apk`.
7. Envie o APK para o celular (por e-mail, WhatsApp ou cabo). Ao abrir, o Android pede para permitir a instalação de “fontes desconhecidas”. Autorize só para este app.

Alternativa: abrir a pasta no **Android Studio** e usar *Build → Build APK(s)*.

## Uso em campo

- **Ditado:** o botão verde 🎤 ao lado de cada campo abre o reconhecimento de voz do Android em português. Para funcionar **sem internet**, baixe o pacote offline: *Configurações → Google → Todos os serviços → Reconhecimento de voz → Idiomas off-line → Português (Brasil)*. O caminho varia conforme a marca do aparelho. O microfone do teclado (Gboard) também funciona em qualquer campo.
- **Frases rápidas:** os botões abaixo de cada campo inserem as frases do laudo modelo. Edite à vontade.
- **Fotos:** cada seção (copa, tronco, raízes, entorno) tem os botões Câmera e Galeria. As fotos tiradas pela câmera também ficam na galeria, em *Imagens/Vistoria Arvores*, como cópia de segurança.
- **Risco:** a matriz ISA calcula o risco de cada linha (parte × alvo). A classificação geral é a maior entre as linhas.
- **Exportar:** os arquivos são salvos em *Download/Vistoria Arvores*.
- **Equipe padrão:** a engrenagem na tela inicial guarda a equipe e o responsável técnico para novas vistorias.

Os dados ficam na memória interna do app. Ao desinstalar o app, os dados são apagados. Exporte as vistorias antes de desinstalar.

## Estrutura

- `app/src/main/assets/www/index.html` – interface, cálculos e geração do laudo e da planilha.
- `app/src/main/java/br/vistoria/arvores/MainActivity.java` – ditado, câmera, armazenamento e exportação.
- `.github/workflows/build-apk.yml` – compilação automática do APK no GitHub.
