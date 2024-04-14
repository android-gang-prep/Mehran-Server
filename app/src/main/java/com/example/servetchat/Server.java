package com.example.servetchat;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Server {

    public interface Callback {
        void onMessageReceived(String message);
    }

    private static final String TAG = "Server";
    private final Callback callback;
    private final List<Socket> clientSockets;
    private ExecutorService executorService;
    private final Handler uiHandler;

    private List<UserModel> users;

    public Server(Callback callback) {
        this.callback = callback;
        this.clientSockets = new ArrayList<>();
        this.executorService = Executors.newCachedThreadPool();
        this.uiHandler = new Handler(Looper.getMainLooper());
        users = new ArrayList<>();
    }

    public void start(int port) {
        executorService.execute(() -> {
            try {
                ServerSocket serverSocket = new ServerSocket(port);
                Log.d(TAG, "Server started on port " + port);

                while (true) {
                    Socket clientSocket = serverSocket.accept();
                    Log.d(TAG, "Client connected: " + clientSocket.getInetAddress().getHostAddress());


                    clientSockets.add(clientSocket);
                    handleClient(clientSocket);
                }
            } catch (IOException e) {
                Log.e(TAG, "Error starting server: " + e.getMessage());
            }
        });
    }

    private void handleClient(Socket clientSocket) {
        executorService.execute(() -> {
            try {
                DataInputStream inputStream = new DataInputStream(clientSocket.getInputStream());
                DataOutputStream outputStream = new DataOutputStream(clientSocket.getOutputStream());


                try {
                    JSONObject usersJson = getJsonObject();
                    outputStream.writeUTF(usersJson.toString());
                } catch (Exception e) {
                    e.printStackTrace();
                }

                while (true) {
                    String message = inputStream.readUTF();
                    Log.d(TAG, "Message received from client: " + message);
                    callback.onMessageReceived(message);

                    try {
                        JSONObject jsonObject = new JSONObject(message);
                        String name = jsonObject.getString("name");
                        String content = jsonObject.getString("content");
                        UserModel userModel = new UserModel(name, content);
                        if (jsonObject.getString("type").equals("online")) {
                            boolean exist = false;
                            for (int i = 0; i < users.size(); i++) {
                                if (users.get(i).getName().equals(name)) {
                                    users.set(i, userModel);
                                    exist = true;
                                    break;
                                }
                            }
                            if (!exist)
                                users.add(userModel);
                            sendMessageToAllClients(getJsonObject().toString(), clientSocket);
                        } else {
                            sendMessageToAllClients(message, clientSocket);
                        }

                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            } catch (IOException e) {
                Log.e(TAG, "Error handling client: " + e.getMessage());
                clientSockets.remove(clientSocket);

                try {
                    clientSocket.close();
                } catch (IOException ioException) {
                    Log.e(TAG, "Error closing client socket: " + ioException.getMessage());
                }
            }
        });
    }


    @NonNull
    private JSONObject getJsonObject() throws JSONException {
        JSONArray jsonArray = new JSONArray();
        for (int i = 0; i < users.size(); i++) {
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("name", users.get(i).getName());
            jsonObject.put("status", users.get(i).getStatus());
            jsonArray.put(jsonObject);
        }
        JSONObject usersJson = new JSONObject();
        usersJson.put("type", "users");
        usersJson.put("content", jsonArray);
        return usersJson;
    }

    public void sendMessageToAllClients(String message, Socket me) {
        executorService.execute(() -> {

            for (Socket socket : clientSockets) {
               // if (socket != me) {
                    try {
                        DataOutputStream outputStream = new DataOutputStream(socket.getOutputStream());
                        outputStream.writeUTF(message);
                        Log.d(TAG, "Message sent to client: " + message);
                    } catch (IOException e) {
                        Log.e(TAG, "Error sending message to client: " + e.getMessage());
                    }
               // }

            }
        });
    }

    public void stop() {
        executorService.shutdown();
        executorService.execute(() -> {
            for (Socket socket : clientSockets) {
                try {
                    socket.close();
                } catch (IOException e) {
                    Log.e(TAG, "Error closing client socket: " + e.getMessage());
                }
            }
            clientSockets.clear();
        });
    }
}
