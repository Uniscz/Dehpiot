# Estado da revisão Dehpilot 0.2.0

## Portão de entrega: bloqueado

Sem APK novo. Código ainda não compilado. Não é uma V1 pronta para instalação. A experiência premium e a compatibilidade real ainda precisam ser verificadas em Android.

As mudanças detalhadas no README estão escritas e integradas à base em fonte. O inventário item a item e as limitações estão em `AUDITORIA-DEHPILOT.md`. Testes escritos e tentativas reais estão em `VALIDACAO.md`.

## Pendências indispensáveis

1. Compilar com SDK 36/Build Tools 36.0.0 e dependências acessíveis; corrigir erros reais de compilação/lint.
2. Executar 65 testes JVM e os 27 testes Android presentes no código. A migração 1→2 deve ser validada com o schema 2 gerado pelo KSP na primeira compilação.
3. Validar captura contínua/overlay, OCR do print real e painel, câmera/galeria, fotos giradas, permissões negadas, navegação/gesto cancelado e morte de processo.
4. Renderizar todas as telas em telefone compacto e fonte ampliada, testar TalkBack, refinar alinhamento e densidade. Não há screenshots desta revisão.
5. Recuperar a especificação original para comparação integral, que não foi possível nesta sessão.
6. Reutilizar a chave de assinatura anterior para atualizar sem desinstalar; ela não veio na fonte enviada.
7. Teste de jornada real: saída da BR depende das coordenadas/lado/sentido e da rede do provedor, não apenas dos 6 km relatados.

## Limites de produto ainda presentes

- Estados de condução e km vazio/tempo real dependem de entrada do motorista. Não há detecção automática confiável de movimento/passageiro.
- Horários/regiões são estatísticas pessoais, com mínimos de amostra; não há forecast calibrado de demanda ou recomendação multirregional de reposicionamento.
- Custos fixos anteriores à migração não têm snapshot de turno; o relatório usa custos registrados dos ciclos, sem inventar custo ocioso antigo.
- Ciclos são atribuídos à data da conclusão. Não há repartição exata do trabalho de uma corrida que atravessa meia-noite.
- O retorno para casa muda o alvo viário e utiliza o custo real desse retorno; não compara simultaneamente uma segunda rota contrafactual para mostrar a economia estratégica.
- O diagnóstico mostra imagem, texto e caixas sobre a imagem importada, com latência OCR e da importação até tentativa de rota. A latência completa da captura de tela até overlay ainda não é medida.
- Não há backup/restauração integral ou importação de viagens do Uber/99. CSV foi preservado e ampliado para exportar todos os dados armazenados.
- Home e filtros leem as últimas 10 mil ofertas/10 mil ciclos; CSV não possui esse corte. Paginação histórica completa ainda é necessária para uso intenso prolongado.
- Histórico de custo conserva até 365 alterações; fotos de câmera temporárias são limpas na próxima abertura de câmera após 24 h.
- Faltam ensaios de consumo de bateria/CPU/memória e validação em fabricantes diferentes.

Não atribuir resultados de testes da 0.1.1 a esta versão.
