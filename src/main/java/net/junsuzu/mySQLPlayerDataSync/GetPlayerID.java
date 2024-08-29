package net.junsuzu.mySQLPlayerDataSync;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class GetPlayerID extends Thread {
    public static int getPlayerID(String playerName) {
        int result = 0;
        try {
            Connection con = MySQLPlayerDataSync.getConnection();
            PreparedStatement pstmt = con.prepareStatement("select * from player where name = ?");
            pstmt.setString(1, playerName);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                result = rs.getInt("id");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return result;


    }
}
