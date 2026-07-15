package org.webservices.testrunner.suites

import org.webservices.testrunner.framework.*
import java.sql.DriverManager

suspend fun TestRunner.postgresDatabaseTests() = suite("Postgres Database Tests") {
test("Postgres transaction commits successfully") {
        val dbConfig = env.endpoints.postgres
        DriverManager.getConnection(dbConfig.jdbcUrl, dbConfig.user, dbConfig.password).use { conn ->
            conn.autoCommit = false
            conn.createStatement().use { stmt ->
                stmt.executeUpdate("CREATE TEMP TABLE test_transaction (id INT, name VARCHAR(50))")
                stmt.executeUpdate("INSERT INTO test_transaction VALUES (1, 'test')")
                conn.commit()

                val rs = stmt.executeQuery("SELECT COUNT(*) FROM test_transaction")
                rs.next()
                rs.getInt(1) shouldBe 1
            }
        }
    }

    test("Postgres connection pool is healthy") {
        
        val dbConfig = env.endpoints.postgres
        val connections = List(5) {
            DriverManager.getConnection(dbConfig.jdbcUrl, dbConfig.user, dbConfig.password)
        }

        connections.forEach { conn ->
            conn.createStatement().use { stmt ->
                val rs = stmt.executeQuery("SELECT 1")
                rs.next()
                rs.getInt(1) shouldBe 1
            }
            conn.close()
        }
    }

    test("Postgres query performance is acceptable") {
        val dbConfig = env.endpoints.postgres
        DriverManager.getConnection(dbConfig.jdbcUrl, dbConfig.user, dbConfig.password).use { conn ->
            fun measurePgActivityCountQuery(): Long {
                val start = System.nanoTime()
                conn.createStatement().use { stmt ->
                    stmt.executeQuery("SELECT COUNT(*) FROM pg_stat_activity").use { rs ->
                        rs.next()
                        val count = rs.getInt(1)
                        require(count > 0) { "pg_stat_activity must include the current test connection" }
                    }
                }
                return (System.nanoTime() - start) / 1_000_000
            }

            // Warm up the JDBC path and system view access before measuring.
            repeat(2) {
                conn.createStatement().use { stmt ->
                    stmt.executeQuery("SELECT 1").use { rs ->
                        rs.next()
                        rs.getInt(1) shouldBe 1
                    }
                }
            }

            val samples = mutableListOf<Long>()
            repeat(5) {
                samples += measurePgActivityCountQuery()
            }

            val sorted = samples.sorted()
            val median = sorted[sorted.size / 2]
            val worst = sorted.last()
            val slowSamples = samples.count { it >= 3000 }

            require(median < 1000) {
                "Median query latency was ${median}ms (samples=${samples.joinToString(",")}), should be under 1 second"
            }
            require(slowSamples <= 1) {
                "Too many slow Postgres metadata queries: slowSamples=${slowSamples}, worst=${worst}ms, samples=${samples.joinToString(",")}"
            }
            require(worst < 5000) {
                "Worst query latency was ${worst}ms (samples=${samples.joinToString(",")}), should stay under 5 seconds"
            }
        }
    }

    test("Postgres foreign key constraints work") {
        val dbConfig = env.endpoints.postgres
        DriverManager.getConnection(dbConfig.jdbcUrl, dbConfig.user, dbConfig.password).use { conn ->
            conn.autoCommit = false
            conn.createStatement().use { stmt ->
                stmt.executeUpdate("""
                    CREATE TEMP TABLE parent_table (id INT PRIMARY KEY, name VARCHAR(50))
                """)
                stmt.executeUpdate("""
                    CREATE TEMP TABLE child_table (
                        id INT PRIMARY KEY,
                        parent_id INT REFERENCES parent_table(id)
                    )
                """)

                stmt.executeUpdate("INSERT INTO parent_table VALUES (1, 'parent')")
                stmt.executeUpdate("INSERT INTO child_table VALUES (1, 1)")

                
                try {
                    stmt.executeUpdate("INSERT INTO child_table VALUES (2, 999)")
                    throw AssertionError("Foreign key constraint did not fire")
                } catch (e: Exception) {
                    val msg = e.message ?: ""
                    require(msg.contains("constraint", ignoreCase = true)) {
                        "Exception should mention constraint violation: $msg"
                    }
                }
            }
        }
    }

    test("Postgres reports the configured database and schema") {
        val dbConfig = env.endpoints.postgres
        DriverManager.getConnection(dbConfig.jdbcUrl, dbConfig.user, dbConfig.password).use { conn ->
            conn.createStatement().use { stmt ->
                stmt.executeQuery("SELECT current_database(), current_schema()").use { rs ->
                    require(rs.next()) { "Postgres did not return its current database and schema" }
                    require(rs.getString(1).isNotBlank()) { "Postgres current_database() was blank" }
                    require(rs.getString(2).isNotBlank()) { "Postgres current_schema() was blank" }
                }
            }
        }
    }
}
