# Jornadas de aceitação — ainda não executadas

Este é um roteiro de execução pendente, não evidência de aprovação. Usar emulador descartável e depois aparelho físico parado; não realizar inspeções interativas enquanto dirige.

| Jornada | Procedimento | Verificações decisivas |
|---|---|---|
| 1. Primeira instalação | Abrir → configurar veículo/consumo/preço/meta → testar print | Nome Dehpilot, custo 4,29/9,4 = 0,456382…/km, demo não cria receita, negar permissões não bloqueia entrada manual |
| 2. Começar a trabalhar | Iniciar turno → sair/voltar/recriar Activity → encerrar | Tempo persiste, segundo toque não alterna indevidamente, custo fixo usa snapshot de início |
| 3. Oferta e decisão | Captura autorizada → OCR → retorno real → resumo → aceitar localmente → concluir | R$/km e R$/h incluem ciclo, origem da rota e incerteza corretas, nenhuma ação no Uber/99, evitar duplicação, análise antiga expira |
| 4. Ofertas fracas | Semear duas janelas com ≥5 ofertas cada e queda ≥20% → abrir Home/mapa | Insight apenas com dados suficientes; nenhuma demanda fictícia; atraso de leitura não vira certeza sobre mercado |
| 5. Abastecimento | Câmera + galeria + EXIF girado + foto ilegível + duas médias → corrigir/salvar | Parser independente, nunca aplica automaticamente, L/100km convertido, histórico antigo não reprecificado |
| 6. Meta e casa | Meta bruta/líquida → prazo após meia-noite → casa salva → calcular retorno → voltar à região de trabalho | ETA condicionada a ritmo positivo, alvo viário correto, região anterior restaurada, custo ainda considera retorno vazio |
| 7. Dia seguinte | Abrir análises → ontem/semana/mês → corrida concluída → comparar | Valores reais, custo histórico preservado, períodos e amostras indicados, sem índice de precisão fictício |
| 8. Navegação | Abrir cada tela interna → botão/gesto voltar → cancelar gesto → voltar entre tabs → restaurar processo | Sai somente da raiz Hoje; cancelamento não faz pop; rascunho e rolagem preservados |
| 9. Migração | Instalar 0.1.1 e criar dados → atualizar com mesma assinatura → abrir 0.2.0 | Room 1→2 preserva IDs/receitas/configurações e custo manual; legado não ganha taxa fixa inventada |
| 10. Acessibilidade/performance | 360dp, 1,0/1,3/2,0 escala de fonte, TalkBack, luz baixa, 10 mil registros | Sem cortes de conteúdo principal, alvos ≥48dp, descrição dos gráficos, sem ANR; medir CPU/memória/bateria reais |

Capturar screenshots de Hoje sem turno/com turno, meta aberta, modo direção, consumo ambíguo e salvo, histórico/lista/detalhe, análises vazias/preenchidas, mapa sem dados/com pontos, onboarding e permissão recusada. Nenhuma dessas capturas existe ainda nesta revisão.

Exceções ainda a resolver: turno esquecido aberto, backup/restauração integral, execução de captura em Android 14–16/fabricantes, estado após cancelamento de seleção de mídia, seleção de casa excluída e atribuição temporal exata de ciclos atravessando meia-noite.
