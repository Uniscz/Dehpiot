# Validação Dehpilot 0.2.0 — 19/09/2026

## Resultado efetivamente observado

| Verificação | Resultado |
|---|---|
| Build inicial da base: `:core:test :app:assembleDebug` | Bloqueado no download do wrapper, antes de executar tarefas |
| Build da revisão: `:core:test :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest :app:connectedDebugAndroidTest` | Bloqueado no mesmo ponto: `java.net.SocketException: Network is unreachable` |
| Android SDK / emulador / adb | Ausentes no ambiente |
| JDK | OpenJDK 17.0.20 disponível |
| Testes JVM executados nesta revisão | **0** |
| Testes Android executados nesta revisão | **0** |
| Lint Android | Não executado |
| APK / assinatura / instalação | Não produzidos / não verificados |
| `git diff --check` | Sem erros de whitespace |
| XML do manifesto e recursos | Parseado sem erro por ElementTree |
| Conferência estática de delimitadores/literais | 25 fontes Kotlin/Gradle verificadas sem desequilíbrio; não é compilação |
| Inspeção visual em emulador | Não executada; não há screenshots |

Logs originais: `evidencias/build-baseline-blocked.log` e `evidencias/build-dehpilot-blocked.log`. Nenhum log de teste bem-sucedido foi recriado.

## Testes escritos, não executados

| Classe | Casos no código | Escopo |
|---|---:|---|
| CoreTest | 29 | Suíte original preservada sem alteração: economia, score, OCR de ofertas, rotas, aprendizado |
| ProductTest | 36 | Combustível, km/L e L/100km, parser de painel, fallback/ambiguidade, custo legado, navegação, metas, sessões, janelas de mercado, calibração, print 99 e retorno adicional |
| IntegrationTest | 9 | Casos originais Android preservados sem alteração |
| DehpilotIntegrationTest | 12 | Migração, preferências, histórico de custo, OCR painel/print real, snapshots, início/fim idempotentes, persistência de casa, alteração de retorno e restauração sucessiva de rascunho |
| UiSmokeTest | 6 | Caso original adaptado aos novos labels + navegação, rascunho, combustível manual, vazio sem dados fictícios e voltar no onboarding |
| **Total** | **92** | **65 JVM + 27 Android; nenhum executado nesta revisão** |

O teste de 6 km usa distância relatada pelo usuário e 10 min de retorno sintéticos para exercitar o cálculo. Não trata essa rota como verificada. O print enviado mostra R$ 32,50, busca de 2,3 km/9 min e viagem de 21,6 km/24 min; com mais 6 km, o bruto por km do ciclo é 32,50/29,9 ≈ R$ 1,087. Esse bruto não representa lucro.

## Como liberar o build

Executar `scripts/verify.sh --device` em emulador descartável com SDK configurado, guardar os XMLs JUnit/lint, gerar schema Room 2, fazer as jornadas de QA e só então assinar/verificar o APK. O teste de interface limpa os dados do aplicativo: não rodar no telefone pessoal com histórico importante.

A documentação original 0.1.1 está em `legacy/VALIDACAO-0.1.1.md`; sua contagem histórica não compõe aprovação da revisão.
