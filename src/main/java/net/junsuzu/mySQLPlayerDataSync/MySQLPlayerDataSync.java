package net.junsuzu.mySQLPlayerDataSync;

import de.tr7zw.nbtapi.NBTCompound;
import de.tr7zw.nbtapi.NBTContainer;
import de.tr7zw.nbtapi.NBTItem;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.ServerLoadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;

public final class MySQLPlayerDataSync extends JavaPlugin implements Listener {

    private static String host;
    private static int port;
    private static String database;
    private static String username;
    private static String password;
    private static Boolean isConnectable = false;
    private static HashMap<String, Boolean> isKickedBecauseOfOnline = new HashMap<String, Boolean>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.getConfig();
        FileConfiguration config = this.getConfig();
        host = config.getString("mysql.host");
        port = config.getInt("mysql.port");
        database = config.getString("mysql.database");
        username = config.getString("mysql.username");
        password = config.getString("mysql.password");
        getServer().getPluginManager().registerEvents(this, this);
    }

    @Override
    public void onDisable() {
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent e) {
        // データベースへの接続が成功している場合
        if (isConnectable) {
            String playerName = e.getPlayer().getName();
            String result = getPlayerData(playerName);
            Connection con = getConnection();
            // プレイヤーデータが存在しない場合
            if (result.isEmpty()) {
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
                        isKickedBecauseOfOnline.put(playerName, true);
                        e.getPlayer().kickPlayer("他の場所でログインしています");
                        return;
                    }
                    else {
                        isKickedBecauseOfOnline.put(playerName, false);
                    }
                    // プレイヤーのIDを取得
                    pstmt = con.prepareStatement("select * from player where name = ?");
                    pstmt.setString(1, playerName);
                    rs = pstmt.executeQuery();
                    int playerId = 0;
                    while (rs.next()) {
                        playerId = rs.getInt("id");
                    }
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
        else {
            e.getPlayer().sendMessage("MySQLPlayerDataSync: データベースへの接続に失敗しました。管理者にお問い合わせください。");
            e.getPlayer().sendMessage("MySQLPlayerDataSync: Failed to connect to the database. Please contact the administrator.");
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent e) {
        // キックされた場合は処理をスキップ
        if (isKickedBecauseOfOnline.get(e.getPlayer().getName())) {
            return;
        }
        // データベースへの接続が成功している場合
        if (isConnectable) {
            String playerName = e.getPlayer().getName();
            Connection con = getConnection();
            try {
                // playerテーブルにプレイヤーデータが存在しない場合は追加
                PreparedStatement pstmt = con.prepareStatement("insert into player (name) select ? from dual where not exists (select * from player where name = ?)");
                pstmt.setString(1, playerName);
                pstmt.setString(2, playerName);
                pstmt.executeUpdate();
                pstmt = con.prepareStatement("select * from player where name = ?");
                pstmt.setString(1, playerName);
                ResultSet rs = pstmt.executeQuery();
                int playerId = 0;
                while (rs.next()) {
                    playerId = rs.getInt("id");
                }
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
        else {
            System.out.println("Could not connect to the database.");
        }
    }

    @EventHandler
    public void onServerLoad(ServerLoadEvent e) {
        createTable();
    }

    public static Connection getConnection() {
        Connection con = null;
        try {
            con = DriverManager.getConnection("jdbc:mysql://" + host + ":" + port + "/" + database, username, password);
            System.out.println("データベースへの接続に成功しました");
            System.out.println("Connected to the database successfully");
            isConnectable = true;
        } catch (SQLException e) {
            e.printStackTrace();
            System.out.println("データベースへの接続に失敗しました");
            System.out.println("Failed to connect to the database");
            isConnectable = false;
        }
        return con;
    }

    public static void createTable() {
        try {
            Connection con = getConnection();
            PreparedStatement pstmt;
            // database.playerテーブルが存在しない場合は作成
            pstmt = con.prepareStatement("create table if not exists player (id int primary key auto_increment, name varchar(255), online tinyint(1) default 0)");
            pstmt.executeUpdate();
            // database.inventoryテーブルが存在しない場合は作成
            pstmt = con.prepareStatement("create table if not exists inventory (id int primary key auto_increment, player_id int, item_id int, amount int, slot int, NBT text)");
            pstmt.executeUpdate();
            // database.enderchestテーブルが存在しない場合は作成
            pstmt = con.prepareStatement("create table if not exists enderchest (id int primary key auto_increment, player_id int, item_id int, amount int, slot int, NBT text)");
            pstmt.executeUpdate();
            System.out.println("テーブルの作成に成功しました");
            System.out.println("Table creation was successful");
        } catch (SQLException e) {
            e.printStackTrace();
            System.out.println("テーブルの作成に失敗しました");
            System.out.println("Failed to create table");
        }
    }

    public static String getPlayerData(String playerName) {
        String result = "";
        try {
            Connection con = getConnection();
            PreparedStatement pstmt = con.prepareStatement("select * from player where name = ?");
            pstmt.setString(1, playerName);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                result = rs.getString("name");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return result;


    }
}