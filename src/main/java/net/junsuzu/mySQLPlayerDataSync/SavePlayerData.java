package net.junsuzu.mySQLPlayerDataSync;

import de.tr7zw.nbtapi.NBTContainer;
import de.tr7zw.nbtapi.NBTItem;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class SavePlayerData extends Thread {
    public PlayerQuitEvent e;
    public void run() {
        String playerName = e.getPlayer().getName();
        Connection con = MySQLPlayerDataSync.getConnection();
        try {
            // playerテーブルにプレイヤーデータが存在しない場合は追加
            PreparedStatement pstmt = con.prepareStatement("insert into player (name) select ? from dual where not exists (select * from player where name = ?)");
            pstmt.setString(1, playerName);
            pstmt.setString(2, playerName);
            pstmt.executeUpdate();
            // プレイヤーIDを取得
            int playerId = GetPlayerID.getPlayerID(playerName);
            // inventoryテーブルにプレイヤーのインベントリデータを追加
            PlayerInventory inventory = e.getPlayer().getInventory();
            // 初期化
            pstmt = con.prepareStatement("delete from inventory where player_id = ?");
            pstmt.setInt(1, playerId);
            pstmt.executeUpdate();
            System.out.println("プレイヤーのインベントリデータを初期化しました: " + playerName);
            System.out.println("Player inventory data has been initialized: " + playerName);
            int slot = 0;
            // プレイヤーのインベントリ全体をループ
            for (ItemStack item : inventory.getContents()) {
                if (item != null) {
                    ItemMeta meta = item.getItemMeta();
                    if (meta != null) {
                        // NBTデータを取得
                        NBTContainer nbt = NBTItem.convertItemtoNBT(new ItemStack(item));
                        String NBT = nbt.toString();
                        // inventoryテーブルにテキストデータを追加
                        pstmt = con.prepareStatement("insert into inventory (player_id, item_id, amount, slot, NBT) values (?, ?, ?, ?, ?)");
                        pstmt.setInt(1, playerId);
                        pstmt.setInt(2, item.getType().ordinal());
                        pstmt.setInt(3, item.getAmount());
                        pstmt.setInt(4, slot);
                        pstmt.setString(5, NBT);
                        pstmt.executeUpdate();
                    }
                }
                slot++;
            }
            System.out.println("プレイヤーのインベントリデータをデータベースに追加しました: " + playerName);
            System.out.println("Player inventory data has been added to the database: " + playerName);
            // playerテーブルのオンライン状態を更新
            pstmt = con.prepareStatement("update player set online = 0 where name = ?");
            pstmt.setString(1, playerName);
            pstmt.executeUpdate();
            System.out.println("プレイヤーのオンライン状態を更新しました: " + playerName);
            System.out.println("Player online status has been updated: " + playerName);
        } catch (SQLException ex) {
            ex.printStackTrace();
        }
    }
}
