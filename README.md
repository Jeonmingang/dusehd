# ServerMenuBridge (Paper 1.21.1 + Velocity 3.3.0, Java 21)

**기능**
- `/servermenu` (alias: `/서버메뉴`) : 플레이어가 **NPC를 바라보고** 입력하면 서버 메뉴 GUI 오픈
- GUI에서 아이콘 클릭 시 **해당 서버로 이동**(Velocity/Bungee 호환 플러그인 메시징 사용)
- `/samlobby reload` : 설정 리로드
- `/samlobby open` : 강제로 GUI 열기(관리자)

**Citizens 연동**
- 의존성 없이 동작. Citizens가 설치되어 있으면 NPC 엔티티에 `NPC` 메타데이터가 달리므로,
  커맨드를 입력할 때 **바라보는 엔티티가 이 메타를 가지면 허용**한다.
- `config.yml`의 `requireNpcSight`를 `false`로 두면 NPC 감지 없이 언제든 메뉴를 열 수 있음.

**Velocity 연결**
- `BungeeCord` 및 `bungeecord:main` 채널로 `Connect` 서브채널 메시지를 보낸다.
- Velocity 3.3.0은 레거시 Bungee 플러그인 메시징을 호환하므로 별도 프록시 플러그인 없이 접속 전환 가능.
  (프록시 설정에서 `bungee-plugin-message-channel = true` 또는 기본 호환 모드가 켜져 있어야 함)

**설치**
1. `plugins/`에 JAR 배치
2. 서버 시작 → `config.yml` 자동 생성
3. `servers` 목록에 서버들(프록시 서버 이름)을 작성
4. Citizens 등 NPC 플러그인으로 로비 NPC 배치 → NPC 바라보고 `/서버메뉴`

**빌드 (Java 21)**
```bash
mvn -V -e -U -DskipTests package
```
`target/ServerMenuBridge-1.0.0-shaded.jar` 생성

**config.yml 예시**
```yaml
menu:
  title: "&l서버 메뉴"
  rows: 3
  requireNpcSight: true
  fillerGlass: true
servers:
  - slot: 11
    icon: EMERALD
    name: "&a서바이벌"
    lore: ["&7기본 생존 서버"]
    connect: survival
  - slot: 13
    icon: NETHER_STAR
    name: "&b로비"
    lore: ["&7로비로 이동"]
    connect: lobby
  - slot: 15
    icon: DIAMOND_SWORD
    name: "&cPVP"
    lore: ["&7전용 PVP"]
    connect: pvp
```

**퍼미션**
- `servermenu.use` (기본 true)
- `servermenu.admin` (OP)

**참고**
- 자동완성/권한 이슈가 있으면 관리자 권한으로 `/samlobby open`으로 먼저 확인.
- Citizens가 없어도 사용할 수 있지만, `requireNpcSight=true`이면 커맨드가 막힌다.
