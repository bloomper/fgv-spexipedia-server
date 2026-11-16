# Spexipedia Server

## Overview

Spexipedia Server is the backend of Spexipedia, FGV's encyclopedia of Chalmersspexet and the other spex, and the source
of the base data (spex categories, spex, revivals, task categories, tasks, characters and numbers) that FGV's other
applications copy. It is a Spring Boot application with a GraphQL API, which answers without login too, with what is
public, a plain HTTP endpoint for image uploads, and an MCP server at `/api/mcp` for AI assistants.
It is backed by MySQL and Keycloak ([fgv-keycloak][1]).
[Spexipedia Web][2] is its frontend and [fgv-spexipedia-deploy][3] runs it on a server.

The product spec is in [fgv-spexipedia-spec][4].

## What comes from where

| Content                                                       | Kept by                                   |
|---------------------------------------------------------------|-------------------------------------------|
| Base data, spex pages, articles, revisions, redirects          | Spexipedia (MySQL)                        |
| Images without people: posters, logos, sketches, scenography, rebus, covers | Spexipedia: originals and WebP/JPEG variants on disk, served by `spexipedia-media` (nginx) under `/media/` |
| Photos of people: group photos, the board, actors              | Spexgram; Spexipedia keeps the item ids and asks Spexgram for signed addresses |
| Who played, held tasks, wore the vest, wrote and arranged      | Spexregister, asked when a page is shown (role `SPEXIPEDIA`), cached in memory for at most ten minutes, never stored |
| Links to tracks, albums, videos, manuscripts and arrangements  | Spexify, Spexflix, Spexarkivet, asked with their read-only roles and cached for a few minutes |

MCP clients log in with the public client `fgv-spexipedia-mcp`; its tokens are accepted only at `/api/mcp`
(`getting-started.adoc`, *MCP server*). Spexregister's MCP server no longer has the base data tools.

The other applications read base data without a token and follow the change feed (`baseDataChanges`, role `SYNC`);
after each change the API calls the consumers' `POST /api/sync/notify` with `fgv-spexipedia-service`'s token. Admins
keep the list of consumers.

## Learning more

The reference documentation is served at `/docs`. Its source is in `src/main/asciidoc`; `getting-started.adoc`
describes how to run the stack locally, the sample data and the test credentials. The GraphQL schema is
`src/main/resources/graphql/schema.graphqls`.

## Building from source

You will need Java 25 or later, ImageMagick (`magick` or `convert`) for the image variants, and Docker for the
integration tests. It is built using [Gradle][5]:

```
./gradlew test              # unit tests and the HTTP API documentation's snippets
./gradlew integrationTest   # integration tests, against MySQL and Keycloak in Testcontainers
./gradlew build             # both, plus the reference documentation
```

## Published images

CI builds the image with Jib and publishes `bloomper/fgv-spexipedia-server` for `linux/amd64` and `linux/arm64`, on
`bloomper/fgv-spexipedia-server-base:25` (`eclipse-temurin:25-jre` plus ImageMagick 7, from `base/Dockerfile`). Uploads
are untrusted: the API tells their format from their first bytes and names ImageMagick's coder itself, and
`base/policy.xml` allows only those coders, no delegates and limited resources:

| Trigger              | Tags                              |
|----------------------|-----------------------------------|
| push to `develop`    | `develop`, `sha-<commit>`         |
| push of tag `vX.Y.Z` | `X.Y.Z`, `latest`, `sha-<commit>` |

To release, set `version` in `build.gradle` to `X.Y.Z`, merge to `main`, then `git tag vX.Y.Z && git push origin vX.Y.Z`.

## Contributing

Contributors to this project agree to uphold its [code of conduct][6]. You can contribute by opening a
[pull request][7]; please see the [contributor guidelines][8].

## License

Spexipedia Server is open source software released under the [Apache 2.0 license][9].

[1]: https://github.com/bloomper/fgv-keycloak
[2]: https://github.com/bloomper/fgv-spexipedia-web
[3]: https://github.com/bloomper/fgv-spexipedia-deploy
[4]: https://github.com/bloomper/fgv-spexipedia-spec
[5]: https://gradle.org
[6]: CODE_OF_CONDUCT.md
[7]: https://help.github.com/articles/using-pull-requests/
[8]: CONTRIBUTING.md
[9]: https://www.apache.org/licenses/LICENSE-2.0.html
