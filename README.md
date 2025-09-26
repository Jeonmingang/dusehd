# ServerGUI (Java 21, Paper 1.21.1)

간단한 서버 선택 GUI 플러그인입니다. Citizens NPC와 연동 가능하며,
BungeeCord 플러그인 메시지 채널을 통해 다른 서버로 이동합니다.

## 요구 사항
- **Java 21**
- **Paper 1.21.1** (또는 호환 빌드)
- (선택) **Citizens** 플러그인 (NPC 우클릭 연동)
- (선택) **Bungee/Velocity 프록시** 및 Plugin Messaging 채널 활성화

## 빌드
```bash
# Maven 3.9+
mvn -q -e -f pom.xml clean package
```
생성 산출물은 `target/ServerGUI-1.0.7.jar` 입니다.

## 설치
`target/*.jar` 를 Paper 서버의 `plugins/` 폴더에 넣고 서버를 시작합니다.

## 명령어
- `/서버 열기` : GUI 열기
- `/서버 연동` : 바라보는 Citizens NPC에 GUI 링크/해제 (권한: `servergui.admin`)
- `/서버 리로드` : 설정 리로드 (권한: `servergui.admin`)

## 설정
`plugins/ServerGUI/config.yml` 에서 서버 버튼을 수정하세요.
예시(동봉된 기본 설정 참고):
```yaml
menu-title: "&6서버 선택"
menu-size: 9
servers:
  - id: "pix121"
    name: "&b픽셀몬 1.21.1"
    material: "NETHER_STAR"
    slot: 3
    lore:
      - "&7NeoForge 1.21.1"
      - "&e클릭하면 이동"
npc:
  linked-ids: []
```

## 주의
- Citizens가 없을 때도 동작하며, NPC 연동 코드에는 안전 가드가 포함되어 있습니다.
- Paper에서 제공하는 Plugin Messaging 채널 `"BungeeCord"` 를 사용합니다.
- 이 소스는 **Java 21** 대상으로 컴파일되며, `pom.xml`의 `maven-compiler-plugin`에서 `<release>21</release>` 로 지정되어 있습니다.
