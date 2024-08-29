package net.junsuzu.mySQLPlayerDataSync;

import de.tr7zw.nbtapi.NBTCompound;
import de.tr7zw.nbtapi.NBTContainer;
import de.tr7zw.nbtapi.NBTItem;
import org.bukkit.Material;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class LoadPlayerData extends Thread {
    public PlayerJoinEvent e;
    public void run() {
        String playerName = e.getPlayer().getName();
        int playerId = GetPlayerID.getPlayerID(playerName);
        Connection con = MySQLPlayerDataSync.getConnection();
        // プレイヤーデータが存在しない場合
        if (playerId == 0) {
            System.out.println("プレイヤーデータが存在しません" + playerName);
            System.out.println("Player data does not exist: " + playerName);
            e.getPlayer().sendMessage("MySQLPlayerDataSync: プレイヤーデータが存在しません。初回ログインでない場合は管理者にお問い合わせください。");
            e.getPlayer().sendMessage("MySQLPlayerDataSync: Player data does not exist. If this is not your first login, please contact the administrator.");
            // プレイヤーデータを追加
            try {
                PreparedStatement pstmt = con.prepareStatement("insert into player (name, online) values (?, 1)");
                pstmt.setString(1, playerName);
                pstmt.executeUpdate();
                System.out.println("プレイヤーデータの追加に成功しました: " + playerName);
                System.out.println("Player data has been added successfully: " + playerName);
            } catch (SQLException ex) {
                ex.printStackTrace();
            }
        } else {
            try {
                // オンラインかどうかを取得
                PreparedStatement pstmt = con.prepareStatement("select * from player where name = ?");
                pstmt.setString(1, playerName);
                ResultSet rs = pstmt.executeQuery();
                int online = 0;
                while (rs.next()) {
                    online = rs.getInt("online");
                }
                // オンラインの場合はキック
                if (online == 1) {
                    MySQLPlayerDataSync.isKickedBecauseOfOnline.put(playerName, true);
                    e.getPlayer().kickPlayer("他の場所でログインしています");
                    return;
                }
                MySQLPlayerDataSync.isKickedBecauseOfOnline.put(playerName, false);
                // プレイヤーのインベントリデータを取得
                pstmt = con.prepareStatement("select * from inventory where player_id = ?");
                pstmt.setInt(1, playerId);
                rs = pstmt.executeQuery();
                PlayerInventory inventory = e.getPlayer().getInventory();
                // プレイヤーのインベントリデータを初期化
                inventory.clear();
                while (rs.next()) {
                    int item_id = rs.getInt("item_id");
                    int amount = rs.getInt("amount");
                    int slot = rs.getInt("slot");
                    String NBT = rs.getString("NBT");
                    // インベントリにアイテムを追加
                    Material[] materials = Material.values();
                    ItemStack is;
                    if (item_id >= 0 && item_id < materials.length) {
                        NBTCompound nbt = new NBTContainer(NBT);
                        is = NBTItem.convertNBTtoItem(nbt);
                        is.setAmount(amount);
                        inventory.setItem(slot, is);
                    }
                    else {
                        System.out.println("アイテムIDが不正です: " + item_id);
                        System.out.println("ItemID is invalid: " + item_id);
                    }
                }
                System.out.println("プレイヤーのインベントリデータを取得しました: " + playerName);
                System.out.println("Player inventory data has been acquired: " + playerName);
                pstmt = con.prepareStatement("update player set online = 1 where name = ?");
                pstmt.setString(1, playerName);
                pstmt.executeUpdate();
                System.out.println("プレイヤーのオンライン状態を更新しました: " + playerName);
                System.out.println("Player online status has been updated: " + playerName);
            } catch (SQLException ex) {
                ex.printStackTrace();
            }
        }
    }
}
