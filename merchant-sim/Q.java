import java.sql.*;
public class Q { public static void main(String[] a) throws Exception {
  try (Connection c = DriverManager.getConnection("jdbc:sqlite:merchant-sim/invoicestudio.db");
       Statement s = c.createStatement()) {
    String[][] qs = {
      {"bills", "SELECT user_id, COUNT(*) FROM bills GROUP BY user_id"},
      {"expenses", "SELECT user_id, COUNT(*) FROM expenses GROUP BY user_id"},
      {"purchase_bills", "SELECT user_id, COUNT(*) FROM purchase_bills GROUP BY user_id"},
      {"buyers", "SELECT user_id, COUNT(*) FROM buyers GROUP BY user_id"},
      {"items", "SELECT user_id, COUNT(*) FROM items GROUP BY user_id"},
      {"templates", "SELECT user_id, COUNT(*) FROM templates GROUP BY user_id"},
      {"auth", "SELECT user_id, remember_me FROM auth_session"},
    };
    for (String[] q : qs) {
      System.out.println("-- " + q[0]);
      try (ResultSet rs = s.executeQuery(q[1])) {
        while (rs.next()) {
          StringBuilder sb = new StringBuilder("   " + rs.getString(1));
          for (int i = 2; i <= rs.getMetaData().getColumnCount(); i++) sb.append(" | ").append(rs.getString(i));
          System.out.println(sb);
        }
      } catch (Exception e) { System.out.println("   ERR " + e); }
    }
  }
}}
