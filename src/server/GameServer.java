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
import java.time.LocalDate;

/**
 *
 * @author amr
 */
public class GameServer {

    ServerSocket serverSocket;
    Connection conn;
    Vector<Game> gamesVector = new Vector<Game>(0);
    Vector<String> availGames = new Vector<String>(0);
    Vector<Client> clientsVector = new Vector<Client>(0);

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
                clientsVector.add(new Client(s));

            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }

    }

    public static void main(String[] args) {
        new GameServer();
    }

    class Client extends Thread {

        InputStreamReader isr;
        BufferedReader br;
        PrintStream ps;
        int isInfoRight = 0;
        int isUsernameAvailable = 1;
        Socket socket;

        public Client(Socket cs) {
            try {
                socket = cs;
                isr = new InputStreamReader(cs.getInputStream());
                br = new BufferedReader(isr);
                ps = new PrintStream(cs.getOutputStream());
                start();
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }

        public void run() {
            try {
                String receivedMessage = "";
                String[] receivedData = null;
                while (true) {
                    receivedMessage = br.readLine();
                    System.out.println(receivedMessage);
                    //if (receivedMessage.indexOf(".") != receivedMessage.length() - 1) {
                    receivedData = receivedMessage.split("[.]");
                    //}

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
                        rs = stm.executeQuery("SELECT * FROM GAME WHERE p1 = '" + receivedData[1] + "' OR p2 = '" + receivedData[1] + "';");
                        while (rs.next()) {
                            String p1 = rs.getString("p1");
                            String p2 = rs.getString("p2");
                            if (p1.equals(receivedData[1])) {
                                ps.println("profile." + rs.getString("p2") + "." + rs.getString("id")
                                        + "." + rs.getString("Date") + "." + rs.getInt("winner") + "." + rs.getInt("p1_recorded"));
                            } else if (p2.equals(receivedData[1])) {
                                ps.println("profile." + rs.getString("p1") + "." + rs.getString("id")
                                        + "." + rs.getString("Date") + "." + rs.getInt("winner") + "." + rs.getInt("p2_recorded"));
                            }
                        }

                        // Create Game
                    } else if (receivedData[0].equals("createGame")) {
                        gamesVector.add(new Game(receivedData[1]));
                        String gameId = gamesVector.get(gamesVector.size() - 1).getId();
                        System.out.println("Game Id: " + gameId);
                        ps.println("createGame." + gameId + "." + receivedData[1]);

                        // Get Available Games
                    } else if (receivedData[0].equals("getGames")) {
                        String games = "getGames.";
                        for (Game game : gamesVector) {
                            if (game.getPlayersNum() == 1) {
                                availGames.add(game.getPlayer1());
                                System.out.println(game.getId() + " added to available games");
                            }
                        }
                        for (String s : availGames) {
                            games += s + ".";
                        }
                        ps.println(games);
                        availGames.removeAllElements();

                        // Join Game
                    } else if (receivedData[0].equals("join")) {
                        String gameId = "";
                        String player1 = "";
                        for (Game game : gamesVector) {
                            if (game.getPlayer1().equals(receivedData[2])) {
                                game.setPlayer2(receivedData[1]);
                                game.setPlayersNum(2);
                                gameId = game.getId();
                                player1 = game.getPlayer1();
                                ps.println("join." + gameId + "." + receivedData[1] + "." + player1);
                                sendMessageToAll("startGame." + gameId + "." + receivedData[1]);
                                String query = "INSERT INTO GAME (id, date, p1, p2) VALUES ('" + game.id + "', '"
                                        + LocalDate.now().toString() + "', '" + game.player1 + "', '" + game.player2 + "');";
                                stm.executeUpdate(query);
                                // Send Whos Turn
                                sendMessageToAll(gameId + ".whosturn." + game.whosTurn);
                                System.out.println(gameId + ".whosturn." + game.whosTurn);
                                break;

                            }
                        }
                        // Whos Turn
                    } else if (receivedData[0].equals("gaming") && receivedData[2].equals("whosturn")) {
                        String gameId = receivedData[1];
                        for (Game game : gamesVector) {
                            if (game.id.equals(gameId)) {
                                sendMessageToAll(gameId + ".whosturn." + game.whosTurn);
                                System.out.println(gameId + ".whosturn." + game.whosTurn);
                                break;
                            }
                        }
                        // Move Played
                    } else if (receivedData[0].equals("gaming") && receivedData[2].equals("moveplayed")) {
                        String gameId = receivedData[1];
                        String mark = receivedData[3];
                        String place = receivedData[4];
                        for (Game game : gamesVector) {
                            if (game.id.equals(gameId)) {
                                if (receivedData[3].equals("X")) {
                                    game.setWhosTurn("O");
                                } else if (receivedData[3].equals("O")) {
                                    game.setWhosTurn("X");
                                }
                                game.setNumOfMoves(game.getNumOfMoves() + 1);
                                String query = "UPDATE GAME SET move" + String.valueOf(game.getNumOfMoves())
                                        + " = '" + mark + place + "' WHERE id = '" + gameId + "';";
                                stm.executeUpdate(query);
                                sendMessageToAll("moveplayed." + gameId + "." + mark + "." + place);
                                // Send Whos Turn
                                if (game.getNumOfMoves() < 9) {
                                    sendMessageToAll(gameId + ".whosturn." + game.whosTurn);
                                    System.out.println(gameId + ".whosturn." + game.whosTurn);
                                }
                                break;
                            }
                        }
                        // Winner
                    } else if (receivedData[0].equals("winner")) {
                        String gameId = receivedData[1];
                        String winnerMark = receivedData[2];
                        String winnerUsername = "";
                        for (Game game : gamesVector) {
                            if (game.getId().equals(gameId)) {
                                if (winnerMark.equals("X")) {
                                    winnerUsername = game.getPlayer1();
                                } else if (winnerMark.equals("O")) {
                                    winnerUsername = game.getPlayer2();
                                } else {
                                    winnerUsername = "draw";
                                }
                                String query = "UPDATE GAME SET winner = '" + winnerUsername + "' WHERE id = '" + gameId + "';";
                                stm.executeUpdate(query);
                                sendMessageToAll("winner." + gameId + "." + winnerUsername);
                                System.out.println("winner is : " + winnerUsername);
                                gamesVector.remove(game);
                                break;
                            }
                        }
                    } else if (receivedData[0].equals("record")) {
                        String gameId = receivedData[2];
                        for (Game game : gamesVector) {
                            if (game.getId().equals(gameId)) {
                                ResultSet rs = stm.executeQuery("SELECT * FROM GAME WHERE id = '" + gameId + "';");
                                while (rs.next()) {
                                    String p1 = rs.getString("p1");
                                    String p2 = rs.getString("p2");
                                    if (p1.equals(receivedData[3])) {
                                        String query = "UPDATE GAME SET p1_recorded = " + receivedData[1] + " WHERE id = '" + gameId + "';";
                                        stm.executeUpdate(query);
                                    } else if (p2.equals(receivedData[3])) {
                                        String query = "UPDATE GAME SET p2_recorded = " + receivedData[1] + " WHERE id = '" + gameId + "';";
                                        stm.executeUpdate(query);
                                    }
                                }

                            }
                        }
                    } else if (receivedData[0].equals("getRecord")) {
                        Thread.sleep(25);
                        ResultSet rs = stm.executeQuery("SELECT * FROM GAME WHERE id = '" + receivedData[1] + "';");
                        while (rs.next()) {
                            ps.println("getRecord." + rs.getString("p1") + "." + rs.getString("p2") + "." + rs.getString("winner") + "."
                                    + rs.getString("move1") + "." + rs.getString("move2") + "." + rs.getString("move3") + "." + rs.getString("move4")
                                    + "." + rs.getString("move5") + "." + rs.getString("move6") + "." + rs.getString("move7") + "."
                                    + rs.getString("move8") + "." + rs.getString("move9"));
                        }
                    }
                }
            } catch (Exception ex) {
            }
        }

        void sendMessageToAll(String msg) {
            for (Client ch : GameServer.this.clientsVector) {
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
    String whosTurn;
    int numOfMoves;

    public Game(String player1) {
        id = UUID.randomUUID().toString().substring(0, 6);
        playersNum = 1;
        this.player1 = player1;
        whosTurn = "X";
        numOfMoves = 0;
    }

    public String getId() {
        return id;
    }

    public void setPlayersNum(int players) {
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

    public void setWhosTurn(String whosTurn) {
        this.whosTurn = whosTurn;
    }

    public String getWhosTurn() {
        return whosTurn;
    }

    public void setNumOfMoves(int numOfMoves) {
        this.numOfMoves = numOfMoves;
    }

    public int getNumOfMoves() {
        return numOfMoves;
    }
}
