# Dehpilot 0.2.0 — checkpoint de implementação, NÃO compilado

Base preservada: ZIP Copiloto 0.1.1 fornecido pelo usuário. Esta revisão é incremental, Android/Compose/Kotlin. Não é um app web e não substitui OSRM, Valhalla, MapLibre, Room, MediaProjection ou ML Kit.

Estado em 19/09/2026: código em desenvolvimento. **Não existe APK novo neste checkpoint. Nenhum teste Gradle ou Android da revisão foi executado.** O ambiente não contém Android SDK, Gradle instalado ou compilador Kotlin. O wrapper falhou ao baixar Gradle 8.11.1: `java.net.SocketException: Network is unreachable`. Log original em `docs/evidencias/build-baseline-blocked.log`.

Implementações até este checkpoint: shell com navegação e gesto preditivo; Home baseada em dados pessoais; meta bruta/líquida e prazo; modos direção/análise; combustível por foto/galeria + confirmação; parser de painel separado; histórico de custos; migração Room 1→2 para custo fixo por turno; análises de período e calibração; mapa com pontos observados; onboarding; diagnóstico secundário; testes novos escritos. Tudo depende de compilação e validação no dispositivo.

A especificação original de aproximadamente 40 páginas não foi recuperada. As fontes efetivamente disponíveis são código/documentos da 0.1.1, relatório anexado e solicitação Dehpilot atual. Não declarar comparação exaustiva com documento não lido.

Próximo passo: completar revisão estática e testes; com JDK 17 + SDK 36 + Build Tools 36.0.0 e internet, executar `./gradlew :core:test :app:assembleDebug :app:lintDebug :app:assembleDebugAndroidTest`, então `:app:connectedDebugAndroidTest` em emulador. Corrigir antes de chamar esta revisão de V1 pronta.
