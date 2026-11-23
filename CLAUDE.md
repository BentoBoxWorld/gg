# CLAUDE.md

Gg is a BentoBox addon that rewards players for positive, helpful chat. Recent chat is sent to an LLM,
which says which lines satisfy which configured challenge; the server validates that and runs reward
commands. `README.md` explains the idea and the design rules behind it. Read its "Design rules" section
before changing behaviour, because the rules below are those rules as they appear in code.

## Commands

```
mvn clean package            # build + all tests; jar lands in target/
mvn test                     # tests only
mvn test -Dtest=JudgeTest    # one class
mvn -q clean package -DskipTests   # compile check
```

JDK 25, Paper 26.3, BentoBox 3.23.7. Dependency versions are properties at the top of `pom.xml`.
`addon.yml` takes its `api-version` from `bentobox.version`, so raising that raises the minimum BentoBox.

## Layout

All code is in `src/main/java/world/bentobox/gg`.

| Class | Job | Thread |
| --- | --- | --- |
| `GgAddon` | Lifecycle, wiring, the polling task, `recordEvent()` for other addons | main, plus `poll()` async |
| `Settings` | Parses `config.yml` into records; `problems()` lists reasons not to start | main |
| `ChatListener` | Chat, joins and advancements into the transcript | chat is async |
| `Transcript` | Synchronized rolling window; assigns sequence numbers; sanitises text | any |
| `PromptBuilder` | Challenges + transcript to prompt text. Pure | async |
| `llm/LlmClient` | One-method interface: system + user text in, reply text out | async |
| `llm/OpenAiCompatibleClient` | The only implementation: `POST {base-url}/chat/completions` | async |
| `VerdictParser` | Reply text to `(line number, challenge id)` pairs. Tolerant of wrapping, strict on shape | async |
| `Judge` | One round: snapshot, ask, parse, validate into `Award`s. Tracks what has been judged | async |
| `RewardManager` | Daily limits, then console commands | main only |

Flow of one round: `GgAddon.poll()` (async) calls `Judge.run()`, then hands any awards to
`RewardManager.grant()` through `runTask` on the main thread.

## Rules that must keep holding

- **The LLM never sees reward commands and never names a player.** It returns a line number; `Judge`
  looks up the author from the transcript. Do not add player names, UUIDs or commands to the reply
  format, and do not put `Challenge.commands()` in a prompt. `PromptBuilderTest` checks the second.
- **Limits are enforced in `RewardManager`, never by asking the model.** Any new way to earn must pass
  through `grant()`.
- **Everything the model returns is validated in `Judge`**: line in range, is chat, newer than
  `judgedUpTo`, challenge exists, not a duplicate. New reply fields need the same treatment.
- **Text entering the transcript goes through `Transcript.sanitize`** (one line, 256 chars). Line breaks
  in chat would let a player forge transcript lines.
- **No network on the main thread; no Bukkit world or command calls off it.** `RewardManager` is
  deliberately not thread-safe.
- **Failure means no rewards.** An `LlmException` leaves `judgedUpTo` unchanged so the same lines are
  retried. An unparseable reply is logged, awards nothing, and is not retried, to avoid paying for the
  same bad reply forever.
- **Never log the API key.** `Settings.Llm.toString()` redacts it; keep it that way.

## Keeping it provider-neutral

There is no vendor SDK and no hard-coded model name, on purpose. `OpenAiCompatibleClient` sends only
`model` and `messages` (plus `response_format` when `llm.json-mode` is on), because optional parameters
such as temperature and token limits are exactly where "compatible" servers differ. A provider with a
different API shape gets its own `LlmClient` implementation, selected in `GgAddon.onEnable()`; do not
bend the existing client around it.

JSON uses Gson and HTTP uses `java.net.http`, both already present on a Paper server. The jar has no
shaded dependencies; keep it that way unless there is a strong reason.

## Tests

`src/test/java/world/bentobox/gg`. JUnit 5, Mockito, MockBukkit.

- `Transcript`, `PromptBuilder`, `VerdictParser`, `Judge`, `RewardManager` and `Settings` tests are plain
  unit tests with no server. `Judge` and `RewardManager` take lambdas for everything external (the LLM,
  the clock, who is online, the command runner), so use those rather than mocking Bukkit.
- `OpenAiCompatibleClientTest` starts a real `com.sun.net.httpserver.HttpServer` on localhost.
- `ChatListenerTest` and `GgAddonTest` extend `CommonTestSetup`, the standard BentoBox addon harness
  (copied from BSkyBlock along with `TestWorldSettings` and `WhiteBox`). `Bukkit` is a static mock with
  deep stubs there: `Bukkit.getPlayer(...)` returns a mock, not null, unless stubbed.
- `GgAddonTest` writes `addon.jar`, `addons/` and `database/` into the project root and deletes them
  afterwards. They are gitignored.
- `GgAddon.start(LlmClient)` is package-private so tests can run a full round with a fake model.

No test calls a real LLM. When changing the prompt, try it against a real model with `dry-run: true` on
a test server, since unit tests cannot tell you whether a prompt judges well.

## Conventions

- Records for data, constructor-injected collaborators, no static state.
- Comments explain why a rule exists, since most of this code is the residue of an exploit somebody
  could try.
- New config keys go in `Settings.from`, `src/main/resources/config.yml` (with a comment), the README
  table and `SettingsTest`.
- Work on a feature branch and open pull requests against `develop`.
