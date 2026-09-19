# Dados e permissões

Sem login, publicidade, analytics ou servidor próprio do aplicativo. OCR executa no aparelho com modelo embarcado. Capturas, texto completo de tela e nomes de passageiros não são gravados como imagens no histórico nem enviados a um serviço de OCR. Uma imagem importada e o texto de diagnóstico permanecem apenas na memória da tela.

As ofertas estruturadas, destinos informados, decisões, custos e resultados observados ficam no banco privado do aplicativo. Preferências e locais confirmados ficam no armazenamento privado. O backup automático e a transferência automática de dados estão desabilitados no manifesto. O banco não tem criptografia adicional própria; usa o isolamento e a proteção do armazenamento do Android. Aparelho comprometido ou acesso privilegiado ao sistema alteram essa proteção.

Rotas enviam coordenadas, opções de condução e eventual rumo ao provedor selecionado. O provedor vê o IP da conexão. O mapa solicita tiles dos locais visualizados. Busca explícita de endereço usa o serviço Geocoder disponível no aparelho. Não use esses recursos online se não desejar essas consultas; desligue as rotas online e evite abrir o mapa. Uma rota válida já em cache pode ser reutilizada durante uma hora.

| Permissão | Finalidade |
|---|---|
| INTERNET / ACCESS_NETWORK_STATE | Rotas e mapas online |
| FOREGROUND_SERVICE / FOREGROUND_SERVICE_MEDIA_PROJECTION | Captura autorizada e serviço com notificação persistente |
| SYSTEM_ALERT_WINDOW | Cartão de rentabilidade sobre a oferta |
| POST_NOTIFICATIONS | Controles visíveis para pausar e parar |
| ACCESS_FINE_LOCATION / ACCESS_COARSE_LOCATION | Obter pontualmente a posição ao tocar em Meu GPS |
| VIBRATE | Feedback opcional de nova oferta |

Não há localização em segundo plano nem AccessibilityService. Arquivos de imagem e CSV usam seletores do sistema, sem permissão ampla de armazenamento. O usuário sempre autoriza MediaProjection pelo Android. Conteúdo protegido ou uma captura revogada não é contornado.

Retenção configurável de 30, 90 ou 365 dias, aplicada ao iniciar o aplicativo. Também é possível excluir registros ou todo o histórico. O cache de rotas pode ser limpo separadamente; locais confirmados podem ser excluídos na interface. CSV é exportado somente quando solicitado e pode conter destinos pessoais. A exportação escapa campos de texto que poderiam ser interpretados como fórmulas por uma planilha.
