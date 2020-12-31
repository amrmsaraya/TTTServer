/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package server;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.*;
import java.util.UUID;
import java.util.Vector;

/**
 *
 * @author amr
 */
public class GameServer {

    ServerSocket serverSocket;
    Connection conn;
    Vector<Game> gamesVector = new Vector<Game>(0);
    Vector<String> availGames = new Vector<String>(0);

    public GameServer() {
        try {
            System.out.println("Accepting...");
            serverSocket = new ServerSocket(6610);
            try {
                Class.forName("org.sqlite.JDBC");
                conn = DriverManager.getConnection("jdbc:sqlite:tictactoe.db");
                System.out.println("Database is Connected");
            } catch (Exception e) {
                System.out.println("Database isn't Connected");
            }
            while (true) {
                Socket s = serverSocket.accept();
                System.out.println("Client Connected!!");
                new Client(s);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }

    }

    public static void main(String[] args) {
        new GameServer();
    }

    class Client extends Thread {

        Vector<Client> clientsVector = new Vector<Client>();
        InputStreamReader isr;
        BufferedReader br;
        PrintStream ps;
        int isInfoRight = 0;
        int isUsernameAvailable = 1;

        public Client(Socket cs) {
            try {
                isr = new InputStreamReader(cs.getInputStream());
                br = new BufferedReader(isr);
                ps = new PrintStream(cs.getOutputStream());
                clientsVector.add(this);
                start();
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }

        public void run() {
            try {
                String receivedMessage = "";
                String[] receivedData;
                while (!(receivedMessage = br.readLine()).equals("close")) {
                    receivedData = receivedMessage.split("[.]");
                    System.out.println(receivedMessage);
                    Statement stm = GameServer.this.conn.createStatement();

                    // Login and Singup
                    if (receivedData[0].equals("signup") || receivedData[0].equals("login")) {
                        ResultSet rs = stm.executeQuery("SELECT * FROM USER;");
                        while (rs.next()) {
                            String username = rs.getString("username");
                            String password = rs.getString("password");
                            if (receivedData[0].equals("login") && receivedData[1].equalsIgnoreCase(username)
                                    && receivedData[2].equalsIgnoreCase(password)) {
                                isInfoRight = 1;
                                break;
                            } else if (receivedData[0].equals("signup") && receivedData[1].equalsIgnoreCase(username)) {
                                isUsernameAvailable = 0;
                            }
                        }
                        if (receivedData[0].equals("login")) {
                            if (isInfoRight == 1) {
                                ps.println("login.correct");
                            } else {
                                ps.println("login.wrong");
                            }
                        }
                        if (receivedData[0].equals("signup")) {
                            if (isUsernameAvailable == 1) {
                                String query = "INSERT INTO USER (username,password) VALUES ('" + receivedData[1] + "', '" + receivedData[2] + "');";
                                System.out.println(query);
                                stm.executeUpdate(query);
                                ps.println("signup.created");

                            } else {
                                ps.println("signup.exist");
                            }
                        }

                        // Profile 
                    } else if (receivedData[0].equals("profile")) {
                        ResultSet rs = stm.executeQuery("SELECT * FROM USER WHERE username = '" + receivedData[1] + "';");
                        while (rs.next()) {
                            ps.println("profile." + rs.getString("username") + "." + rs.getInt("totalGames")
                                    + "." + rs.getInt("win") + "." + rs.getInt("lose") + "." + rs.getInt("draw"));
                        }

                        // Create Game
                    } else if (receivedData[0].equals("createGame")) {
                        gamesVector.add(new Game(receivedData[1]));
                        String gameId = gamesVector.get(gamesVector.size() - 1).getId();
                        System.out.println(gameId);
                        ps.println("createGame." + gameId + "." + receivedData[1]);

                        // Get Available Games
                    } else if (receivedMessage.equals("getGames.")) {
                        String games = "getGames.";
                        for (Game game : gamesVector) {

                            if (game.getPlayersNum() == 1) {
                                availGames.add(game.getPlayer1());
                            }
                        }
                        for (int i = 0; i < availGames.size(); i++) {
                            if (i != availGames.size() - 1) {
                                games += availGames.get(i) + ".";
                            } else {
                                games += availGames.get(i);
                            }
                        }
                        availGames.setSize(0);
                        ps.println(games);

                        // Join Game
                    } else if (receivedData[0].equals("join")) {
                        String gameId = "";
                        String player1 = "";
                        for (Game game : gamesVector) {
                            if (game.player1.equals(receivedData[2])) {
                                game.setPlayer2(receivedData[1]);
                                game.setPlayers(2);
                                gameId = game.getId();
                                player1 = game.getPlayer1();
                                ps.println("join." + gameId + "." + receivedData[1] + "." + player1);
                                sendMessageToAll("startGame." + gameId + "." + receivedData[1]);
                                break;
                            }
                        }

                        // Get Available Games
                    }
                    System.out.println("Client Disconnected!!");
                }
            } catch (Exception ex) {
                clientsVector.remove(this);
            }
        }

        void sendMessageToAll(String msg) {
            for (Client ch : clientsVector) {
                try {
                    ch.ps.println(msg);
                } catch (Exception ex) {
                    System.out.println("Connection has been closed by client!");
                }
            }
        }
    }
}

class Game {

    String id;
    String player1, player2;
    int playersNum;

    public Game(String player1) {
        id = UUID.randomUUID().toString().substring(0, 6);
        playersNum = 1;
        this.player1 = player1;
    }

    public String getId() {
        return id;
    }

    public void setPlayers(int players) {
        this.playersNum = players;
    }

    public int getPlayersNum() {
        return playersNum;
    }

    public String getPlayer1() {
        return player1;
    }

    public void setPlayer2(String player2) {
        this.player2 = player2;
    }

    public String getPlayer2() {
        return player2;
    }
}
