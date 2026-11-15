# Icon Studio

Aplicação Android nativa para desenho e criação de ícones.

## Funcionalidades nesta primeira versão
- Canvas de desenho com traço livre, linha, retângulo, círculo e borracha.
- Paleta de cores e controlo de espessura.
- Zoom por controlos no canvas.
- Camadas com seleção, visibilidade e remoção.
- Desfazer e refazer.
- Guardar o estado do projeto localmente.
- Exportar PNG quadrado 1024 × 1024 para Pictures/IconStudio.
- GitHub Actions compila APK Debug, APK Release não assinado e AAB Release.

## Construção
Requer JDK 17 e Gradle 8.9 ou compatível com Android Gradle Plugin 8.7.3.

```bash
gradle assembleDebug assembleRelease bundleRelease
```

Os artefactos ficam em `app/build/outputs/`.

## Estado
Esta é uma primeira implementação funcional em evolução. Ainda não inclui importação SVG/PNG, edição de nós Bézier, texto vetorial, gradientes, filtros, exportação SVG/PDF/ICO, gestão de ficheiros de projeto, nem assinatura de release. A build de release gerada pelo workflow é não assinada até configurar credenciais de assinatura de forma segura no GitHub Actions.
