package net.junsuzu.mySQLPlayerDataSync;

import net.junsuzu.mySQLPlayerDataSync.GetPlayerID;
import net.junsuzu.mySQLPlayerDataSync.SavePlayerData;
import net.junsuzu.mySQLPlayerDataSync.LoadPlayerData;

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
    public static HashMap<String, Boolean> isKickedBecauseOfOnline = new HashMap<String, Boolean>();

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
            LoadPlayerData loadPlayerData = new LoadPlayerData();
            loadPlayerData.e = e;
            loadPlayerData.start();
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
            SavePlayerData savePlayerData = new SavePlayerData();
            savePlayerData.e = e;
            savePlayerData.start();
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
}
