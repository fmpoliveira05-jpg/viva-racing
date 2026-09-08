# Viva Racing

Aplicação Android para acompanhamento colaborativo de maratonas, corridas e provas de ciclismo.
Trabalho prático da unidade curricular de **Computação Móvel e Ubíqua** (Época Especial), Licenciatura em Engenharia Informática, ESTG, Universidade Técnica do Porto, ano letivo 2025/2026.

| | |
|---|---|
| **Aluno** | Francisco Miguel Pereira Oliveira (8230148) |
| **Docente** | Fábio André Souto da Silva |
| **Linguagem** | Kotlin |
| **IDE** | Android Studio |
| **minSdk / targetSdk** | 26 / 35 |

---

## 1. Contexto e tema

A Viva Racing permite que a comunidade de utilizadores registe e acompanhe provas desportivas em modelo de *crowdsourcing*. Não existe uma entidade central a alimentar os dados: são os próprios utilizadores que criam as provas, gravam os percursos, publicam alertas ao longo do trajeto e registam os seus tempos pessoais.

O tema cobre integralmente o cenário proposto no enunciado:

- registo de provas para a comunidade;
- registo do percurso de uma prova oficial ao vivo (com o dispositivo) **ou** por importação de ficheiros KML;
- subscrição de provas próprias ou de terceiros e, numa secção dedicada de **atletas**, subscrição de qualquer utilizador registado, passando a receber avisos das suas posições em qualquer prova;
- **histórico de passagens de um atleta**, acessível a quem o segue ou a quem partilha com ele pelo menos uma prova subscrita, e limitado aos alertas das provas que o próprio atleta subscreveu;
- **amizades**: pesquisa no diretório público, envio de pedido, caixa de pedidos recebidos e enviados, aceitar ou recusar, com limites de 25 amigos e 20 pedidos pendentes;
- **visibilidade** por prova, pública, apenas amigos ou privada, herdada pelos alertas e participações associados;
- **modo convidado** (sessão anónima), com acesso apenas de leitura às provas públicas;
- publicação de alertas de início de prova, passagem de atletas (com número de dorsal, no local do observador) e fim de prova;
- anexação de fotografias aos alertas e às provas;
- consulta de alertas das provas subscritas;
- notificações de posições de atletas;
- histórico disponível online (Firestore) e offline (Room/SQLite);
- modo amador, com cronometragem do tempo pessoal no percurso de uma prova, publicável com nome ou de forma anónima.

Todas as listagens existem nos dois formatos exigidos, ou seja, **lista + detalhe** e **mapa + detalhe**. O ecrã de lista é próprio de cada tipo de registo, enquanto a representação em mapa vive num **mapa único** com três camadas ligáveis de forma independente (provas, alertas e participações amadoras). Tocar num marcador abre exatamente o mesmo ecrã de detalhe a que a lista conduz.

---

## 2. Arquitetura

O projeto segue uma arquitetura **MVVM em três camadas**, com fluxo unidirecional de dados.

```
UI (Jetpack Compose)
  └── ViewModels (AndroidViewModel + StateFlow)
        └── Repositórios
              ├── Room (SQLite)   ← fonte única de verdade para a UI
              ├── Firestore       ← base de dados online, sincronizada em tempo real
              ├── Retrofit        ← OpenWeatherMap, Nominatim, Supabase Storage
              └── Serviços do sistema (localização, sensores, notificações)
```

**Fonte única de verdade.** Os ecrãs nunca leem diretamente do Firestore: leem sempre do Room. O `SyncManager` mantém ouvintes em tempo real sobre as coleções do Firestore e escreve cada emissão na base de dados local. Consequência prática: a aplicação funciona integralmente sem rede, apresentando a última cópia conhecida.

**Injeção de dependências manual.** O `AppContainer` cria e expõe, de forma preguiçosa, todas as dependências partilhadas. Optou-se por não usar Hilt: o grafo é pequeno, fica inteiramente explícito e evita-se mais um processador de anotações no tempo de compilação. Todos os ViewModels estendem `AndroidViewModel`, pelo que a fábrica predefinida do `ViewModelProvider` os instancia sem código adicional.

### Estrutura de pastas

```
core/          utilitários transversais (localização, notificações, preferências,
               sensores de sistema, formatação, KML, interações Android)
data/
  local/       entidades, DAOs e base de dados Room
  remote/      Firebase (Auth + Firestore) e APIs REST (Retrofit)
  repository/  repositórios que conciliam local e remoto
  model/       modelos de domínio independentes de tecnologia
service/       serviço em primeiro plano (percurso), serviço bound (sensores), FCM
worker/        trabalho periódico (WorkManager) e respetivo agendamento
ui/
  navigation/  Navigation Drawer + Scaffold + NavHost
  components/  componentes reutilizáveis (cartões, mapa, estados vazios)
  screens/     um pacote por área funcional, cada um com ecrã(s) + ViewModel
               (races, alerts, amateur, map, social, tracking, camera,
                profile, settings, sensors, auth, home, info)
  theme/       cor, tipografia e tema Material 3
```

---

## 3. Requisitos obrigatórios e onde estão implementados

| Requisito | Implementação |
|---|---|
| Localização em vários idiomas | `values/` (EN), `values-pt/` (PT), `values-es/` (ES) + seletor em Definições (`LocaleManager`) |
| Material Design e Jetpack Compose | Material 3 completo, cores dinâmicas em Android 12+, tema claro/escuro |
| Navigation Drawer + Scaffold + Navigation | `ui/navigation/VivaRacingRoot.kt`, `AppDrawer.kt`, `VivaRacingNavHost.kt` |
| Interação com elementos Android | `core/util/AndroidInteractions.kt`: *Dialer*, Mensagens, Partilha, Contactos, Mapas externos |
| Imagens locais | Vetores em `res/drawable` (logótipo, ícones de notificação, ícone adaptativo) |
| Base de dados online com cache local | Room + Firestore, com marcação `pendingSync` para escritas feitas offline |
| Informação pública e privada em Firebase | Conteúdo com visibilidade por documento (`visibleTo`); diretório `publicProfiles` legível por todos; `users/{uid}` e subcoleções (perfil, subscrições, amigos, pedidos) restritas ao próprio. Regras em `firestore.rules` |
| API REST com Retrofit | OpenWeatherMap em dois endpoints (condições atuais e previsão a 5 dias para a hora de partida), Nominatim (pesquisa de locais) e Supabase Storage (imagens) |
| Adaptação a eventos do sistema | `SystemStateMonitor`: bateria, carregamento, estado térmico, rede limitada, poupança de dados e restrição de segundo plano → perfil de recolha (alto / equilibrado / mínimo). Sensor de luz comuta o tema |
| Serviços Android | `RouteTrackingService` (primeiro plano, tipo *location*), `SensorMonitorService` (*bound*), `VivaRacingMessagingService` (FCM) |
| Notificações | Dois canais distintos; notificação persistente do serviço e notificações da comunidade |
| Localização em mapas e listas separados | Cada tipo de registo tem o seu ecrã de lista e está representado no mapa único (`ui/screens/map/MapScreen.kt`) numa camada própria, ligável de forma independente |
| Sensores (além do GPS) | Luminosidade ambiente, acelerómetro, proximidade e contador de passos |
| Componente social | Amizades e subscrição de atletas em secções separadas: em Amigos gere-se a relação de amizade, em Atletas segue-se ou deixa-se de seguir e abre-se o histórico de passagens |

### Elementos de bonificação

- **CameraX**, captura de fotografias para os alertas (`ui/screens/camera/CameraCapture.kt`), com `LifecycleCameraController`, `PreviewView` integrado em Compose e alternância entre câmaras.
- **API de locais**, Nominatim (OpenStreetMap) para pesquisa e geocodificação inversa, apoiando a decisão de onde realizar a atividade física.
- **Notificações com a aplicação encerrada**, `CommunityUpdatesWorker` (WorkManager, periódico, com restrições de rede e bateria) deteta alertas em provas subscritas, passagens de atletas subscritos, provas novas de amigos e participações novas de amigos, respeitando a visibilidade e as preferências de notificação.

---

## 4. Bibliotecas externas

| Biblioteca | Versão | Justificação |
|---|---|---|
| Jetpack Compose (BOM) | 2024.12.01 | Interface declarativa; Material 3 |
| Navigation Compose | 2.8.5 | Navegação entre ecrãs com argumentos tipados |
| Room | 2.6.1 | Persistência local com verificação de SQL em compilação |
| WorkManager | 2.10.0 | Trabalho periódico fiável, tolerante a reinícios |
| Retrofit + Gson | 2.11.0 | Cliente REST com conversão automática de JSON |
| OkHttp (+ logging) | 4.12.0 | Cliente HTTP partilhado e diagnóstico de pedidos |
| Firebase BOM (Auth, Firestore, Messaging) | 33.7.0 | Autenticação, base de dados online e mensagens push |
| Play Services Location | 21.3.0 | Cliente de localização fundida (*fused*) |
| maps-compose + Play Services Maps | 6.4.1 / 19.0.0 | Mapas nativos em Jetpack Compose |
| CameraX | 1.4.1 | Captura de fotografias (bonificação) |
| Coil | 2.7.0 | Carregamento assíncrono de imagens remotas |
| Accompanist Permissions | 0.36.0 | Pedido de permissões em Compose |
| Guava (android) | 33.3.1 | Coloca `ListenableFuture` no classpath de compilação (exposto pelas APIs assíncronas da CameraX e do WorkManager) |
| AndroidX Preference | 1.2.1 | `SharedPreferences` predefinidas, como lecionado |

---

## 5. Configuração do projeto

### 5.1 Chaves necessárias

As chaves **não** são versionadas. Copie `local.properties.example` para `local.properties`, na raiz do projeto, e preencha os valores:

```properties
MAPS_API_KEY=<chave do Google Maps SDK for Android>
WEATHER_API_KEY=<chave da OpenWeatherMap>
SUPABASE_URL=https://<id-do-projeto>.supabase.co
SUPABASE_ANON_KEY=<anon key do Supabase>
SUPABASE_BUCKET=viva-racing
```

O ficheiro `app/build.gradle.kts` lê estes valores e injeta-os no `AndroidManifest.xml` (Maps) e na classe `BuildConfig` (restantes).

### 5.2 Firebase

1. Criar um projeto na consola Firebase.
2. Adicionar uma aplicação Android com o *package* `pt.ipp.estg.cmu.vivaracing` e a impressão digital SHA-1 obtida com a tarefa Gradle `signingReport`.
3. Transferir o `google-services.json` e colocá-lo em `app/`. O ficheiro não é versionado, por isso cada pessoa que compile o projeto tem de gerar o seu.
4. Ativar os métodos de autenticação **Email/Palavra-passe** e **Anónimo** (este último sustenta o modo convidado).
5. Criar a base de dados **Cloud Firestore** e publicar o conteúdo de `firestore.rules`.

### 5.3 Supabase Storage

1. Criar um projeto em supabase.com.
2. Criar um *bucket* público chamado `viva-racing`.
3. Criar sobre `storage.objects` uma política de `INSERT` para o papel `anon`, com a condição `bucket_id = 'viva-racing'`. Um *bucket* público concede apenas leitura, por isso sem esta política o envio das fotografias é recusado com `new row violates row-level security policy`.
4. Copiar o *Project URL* e a *anon public key* para `local.properties`.

### 5.4 Compilar

O projeto inclui o *wrapper* do Gradle 8.9, pelo que não é preciso instalar o Gradle à parte.

```bash
./gradlew assembleDebug     # gerar o APK de depuração
./gradlew test              # testes unitários
./gradlew connectedCheck    # testes instrumentados (requer emulador ou dispositivo)
```

---

## 6. Testes

- **Unitários** (`app/src/test`), com `GeoUtilsTest` valida o cálculo de distâncias (Haversine), o comprimento de percursos e a regra de proximidade ao trajeto; `FormattersTest` valida a formatação de durações, distâncias e ritmo.
- **Instrumentados** (`app/src/androidTest`), com `RaceDaoTest` valida a persistência do percurso em JSON, a operação de *upsert*, a marcação de sincronização e a consulta que cruza provas com subscrições.

---

## 7. Decisões de implementação relevantes

**Percurso guardado como JSON numa coluna.** Um percurso é sempre lido em bloco; uma tabela de relação obrigaria a uma junção por cada leitura sem qualquer benefício. O `TypeConverter` do Room serializa a lista de pontos com Gson.

**Escritas offline com marcação de estado.** Cada criação é escrita primeiro no Room e só depois enviada ao Firestore, com limite de tempo. Se o envio não concluir, o registo fica marcado como `pendingSync`, estado visível na interface, e é reenviado pelo `PendingUploadWorker` ou na sincronização seguinte. O Firestore mantém, em paralelo, a sua própria fila de escritas offline.

**Idioma por aplicação.** Em vez de manipular a `Configuration` e chamar `recreate()`, usa-se `AppCompatDelegate.setApplicationLocales`. A preferência passa a ser persistida pelo sistema, fica visível nas definições do Android e a recomposição dos ecrãs é automática.

**Estado do serviço partilhado por `StateFlow`.** O `RouteTrackingService` publica o seu estado num `StateFlow` do objeto companheiro. Assim, a interface pode ser destruída e recriada sem interromper a gravação nem perder o progresso acumulado.

**Visibilidade num campo consultável.** A visibilidade é materializada no campo `visibleTo`, uma lista de marcadores (`public`, `u:<autor>`, `f:<autor>`). O cliente consulta com `whereArrayContainsAny` usando os marcadores a que tem direito, o que resolve a filtragem numa única consulta sem índices compostos. O limite de 25 amigos decorre daqui: o operador aceita no máximo 30 valores por consulta.

**Base de dados local em quatro versões.** O esquema do Room começou com as sete entidades do domínio desportivo e cresceu à medida que a componente social foi acrescentada, sempre por migração explícita e nunca por reinstalação. A `MIGRATION_1_2` acrescentou os campos de visibilidade, a `MIGRATION_2_3` criou as quatro tabelas sociais (amigos, pedidos, subscrições de atletas e diretório público) e a `MIGRATION_3_4` acrescentou ao diretório público a lista de provas subscritas por cada utilizador. A base está hoje na versão 4, com dez entidades e dez DAO.

**Totais sociais publicados no perfil público.** As subcoleções de amigos e de pedidos de um utilizador são privadas, pelo que nenhum outro cliente as pode ler. Como o envio de um pedido de amizade tem de respeitar os limites de capacidade do destinatário, cada utilizador publica no seu próprio `publicProfiles/{uid}` apenas dois totais, o número de amigos e o número de pedidos pendentes, mais a lista de provas que subscreveu. Quem envia o pedido lê esses valores, que não revelam quem são os amigos nem de quem vieram os pedidos. A mesma lista de provas sustenta a regra de acesso ao histórico de passagens.

**Histórico de passagens com acesso condicionado.** O histórico de um atleta só fica visível a quem o segue ou a quem subscreveu pelo menos uma prova que ele também subscreveu, situação em que ambos acompanham o mesmo evento. Mesmo nesse caso, mostra apenas os alertas das provas que o próprio atleta subscreveu.

**Modo convidado com autenticação anónima.** O visitante é, para o Firestore, um utilizador autenticado, pelo que as regras podem continuar a exigir sessão em todas as leituras. As mesmas regras distinguem-no pelo fornecedor de autenticação e recusam qualquer escrita.

**Perfil público separado do privado.** `publicProfiles` contém apenas nome e localidade, tornando a pesquisa de amigos possível sem expor telemóvel, dorsal ou email, que ficam em `users/{uid}`.

**Regra de proximidade.** Só é possível publicar alertas de passagem de atleta a menos de 250 m do percurso, calculados com a fórmula de Haversine. Quem está longe consulta as atualizações publicadas por terceiros, exatamente como o enunciado descreve.

**Eficiência energética.** O trabalho periódico exige rede e bateria não fraca, usa uma janela flexível de 10 minutos e política de recuo exponencial. Com bateria fraca, o intervalo de localização passa de 5 s para 20 s e a prioridade de GPS de alta precisão para equilibrada.
