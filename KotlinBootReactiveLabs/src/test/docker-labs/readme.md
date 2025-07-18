
## PostgreSQL DDL

```bash
docker exec -i docker-labs-postgres-db-1 psql -U postgres -t < create_tables_postgres.sql
```

## MySQL DDL

```bash
docker exec -i docker-labs-mysql8-1 mysql -u root -proot -e "CREATE SCHEMA IF NOT EXISTS neo;"
```

```bash
docker exec -i docker-labs-mysql8-1 mysql -u root -proot -D neo < create_tables_mysql.sql
```
