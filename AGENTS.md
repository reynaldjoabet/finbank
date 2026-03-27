## Agent Guidelines

If `sbt --client` is causing trouble, try:

```bash
sbt --client shutdown 2>/dev/null; pkill -f sbt 2>/dev/null; rm -rf .bsp project/target/active.json project/target/.sbt-server-connection.json
```