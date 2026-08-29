# VeloxTrade development checklist

- [x] Clarify requirements: C++20, Spring Boot 3.4, FastAPI, Angular 19, cloud-native infrastructure.
- [x] Scaffold the monorepo with independently buildable services.
- [x] Implement the real matching engine, persistence, JWT auth, analytics, and dashboard.
- [x] Add test suites for all four services and wire them into CI.
- [x] Validate what the local toolchain allows: Angular build + 6 Karma specs and 9 pytest tests pass.
- [x] Keep the README, compose file, Helm chart, and CI workflow current.

## Conventions

- Service boundaries are **HTTP/JSON**. The C++ engine has no third-party dependencies; do not add a codegen step.
- All market data is simulated; never represent it as investment advice or real execution.
- Prefer small, independently deployable services and environment-based configuration.
- Never commit secrets. `VELOXTRADE_JWT_SECRET` and database passwords come from the environment.
- Angular uses standalone components, signals, `OnPush`, and relative `/api` URLs (proxied in dev by `proxy.conf.json`, in prod by nginx).

## Verification

- Engine: `ctest --test-dir build/engine --output-on-failure`
- Platform: `mvn -f platform-java/pom.xml test`
- Analytics: `pytest` in `ml-python/`
- Dashboard: `npx ng test --watch=false --browsers=ChromeHeadless` and `npm run build` in `dashboard-angular/`

JDK, Maven, CMake/C++ compilers, and Docker are not installed in this environment. Java, C++, and container builds are verified by CI only.
