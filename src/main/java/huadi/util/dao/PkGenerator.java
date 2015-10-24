package huadi.util.dao;

import java.sql.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * PrimaryKeyGenerator. Based on MySQL.
 *
 * @author HUADI
 * @see PkGenerator#main(String[]) for example
 */
public class PkGenerator {
    static {
        try {
            Class.forName("com.mysql.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            throw new Error("No JDBC driver for MySQL.");
        }
    }

    private static final String HOST = "MySQL host";
    private static final int PORT = 3306;
    private static final String DB = "db name";

    private String connStr = "jdbc:mysql://" + HOST + ":" + PORT + "/" + DB;
    private String user = "user";
    private String pwd = "password";

    /*
        CREATE TABLE `pk_sequence` (
          `id` int(11) NOT NULL AUTO_INCREMENT,
          `k` varchar(16) NOT NULL,
          `v` bigint(20) NOT NULL,
          `step` int(10) unsigned NOT NULL,
          `modify_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
          PRIMARY KEY (`id`),
          UNIQUE KEY `IDX_k_UNIQUE` (`k`)
        )
     */
    private String sequenceTableName = "pk_sequence";
    private String keyColumn = "k";
    private String valueColumn = "v";
    private String stepColumn = "step";


    private String keyName = "user_id";

    private PrimaryKey pk = new PrimaryKey(0, -1); // 初始化一个不能用的PK, 第一次使用会触发更新. 省得做null判断.


    public Long get() {
        long primaryKey = pk.next();
        if (primaryKey == PrimaryKey.INVALID_VALUE) {
            synchronized (this) {
                while ((primaryKey = pk.next()) == PrimaryKey.INVALID_VALUE) {
                    pk = load();
                }
            }
        }

        return primaryKey;
    }

    private PrimaryKey load() {
        Connection conn = null;
        ResultSet rs = null;
        PreparedStatement stmt = null;
        try {
            conn = DriverManager.getConnection(connStr, user, pwd);

            int retry = 0;
            while (true) {
                stmt = conn.prepareStatement(getSelectSql());
                stmt.setString(1, keyName);
                rs = stmt.executeQuery();
                rs.next();
                Long oldValue = rs.getLong(valueColumn);
                Long stepValue = rs.getLong(stepColumn);
                Long newValue = oldValue + stepValue;

                try {
                    rs.close();
                } catch (SQLException e) {
                    System.err.println("Exception on close resource." + e);
                }
                try {
                    stmt.close();
                } catch (SQLException e) {
                    System.err.println("Exception on close resource." + e);
                }

                stmt = conn.prepareStatement(getUpdateSql());
                stmt.setLong(1, newValue);
                stmt.setString(2, keyName);
                stmt.setLong(3, oldValue);
                int affectedRows = stmt.executeUpdate();
                if (affectedRows != 0) {
                    return new PrimaryKey(oldValue + 1, oldValue + stepValue);
                }

                if (++retry > 10) {
                    System.err.println(
                            "PK generate failed " + retry + " times. KeyName: \"" + keyName + "\", old: " + oldValue
                                    + ", new: " + newValue + ", step: " + stepValue + ".");
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Exception on generating primary key.", e);
        } finally {
            if (rs != null) {
                try {
                    rs.close();
                } catch (SQLException ignored) {}
            }
            if (stmt != null) {
                try {
                    stmt.close();
                } catch (SQLException ignored) {}
            }
            if (conn != null) {
                try {
                    conn.close();
                } catch (SQLException ignored) {}
            }
        }
    }

    private String getSelectSql() {
        return "SELECT * FROM " + sequenceTableName + " WHERE " + keyColumn + "=?";
    }

    private String getUpdateSql() {
        return "UPDATE " + sequenceTableName + " SET " + valueColumn + "=? WHERE " + keyColumn + "=? AND " +
                valueColumn + "=?";
    }

    /**
     * pk和max必须保证原子更新, 所以这里用一个class封装.
     */
    private static class PrimaryKey {
        private static final long INVALID_VALUE = Long.MIN_VALUE;

        private AtomicLong pk;
        private long max;

        private PrimaryKey(long pk, long max) {
            this.pk = new AtomicLong(pk);
            this.max = max;
        }

        long next() {
            long v = pk.getAndIncrement();
            return v > max ? INVALID_VALUE : v;
        }
    }


    public String getConnStr() {
        return connStr;
    }

    /**
     * @param connStr jdbc:mysql://mysqlhost:3306/dbname 注意要有db名
     */
    public void setConnStr(String connStr) {
        this.connStr = connStr;
    }

    public String getUser() {
        return user;
    }

    /**
     * @param user 连接用户名
     */
    public void setUser(String user) {
        this.user = user;
    }

    public String getPwd() {
        return pwd;
    }

    /**
     * @param pwd 连接密码
     */
    public void setPwd(String pwd) {
        this.pwd = pwd;
    }

    public String getSequenceTableName() {
        return sequenceTableName;
    }

    /**
     * @param sequenceTableName 生成sequence使用的表名
     */
    public void setSequenceTableName(String sequenceTableName) {
        this.sequenceTableName = sequenceTableName;
    }

    public String getKeyColumn() {
        return keyColumn;
    }

    /**
     * @param keyColumn sequence表key的column名
     */
    public void setKeyColumn(String keyColumn) {
        this.keyColumn = keyColumn;
    }

    public String getValueColumn() {
        return valueColumn;
    }

    /**
     * @param valueColumn sequence表value的column名
     */
    public void setValueColumn(String valueColumn) {
        this.valueColumn = valueColumn;
    }

    public String getStepColumn() {
        return stepColumn;
    }

    /**
     * @param stepColumn sequence表step的column名
     */
    public void setStepColumn(String stepColumn) {
        this.stepColumn = stepColumn;
    }

    public String getKeyName() {
        return keyName;
    }

    /**
     * @param keyName sequence名, 对应sequence表key字段的值
     */
    public void setKeyName(String keyName) {
        this.keyName = keyName;
    }


    /**
     * Example
     */
    public static void main(String[] args) {
        PkGenerator pkGenerator = new PkGenerator();
        pkGenerator.setKeyName("user_id");
        for (int i = 0; i < 20; i++) {
            System.out.println(pkGenerator.get());
        }
    }
}