import java.io.*;
import java.net.*;
import java.util.*;

public class servidor {
    private static final int PUERTO = 5000;

    public static void main(String[] args) {
        try (ServerSocket servidor = new ServerSocket(PUERTO)) {
            System.out.println("Servidor iniciado en el puerto " + PUERTO);

            while (true) {
                Socket socket = servidor.accept();
                System.out.println("Cliente conectado");
                new Thread(new ManejadorCliente(socket)).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

class ManejadorCliente implements Runnable {
    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private String usuarioLogueado = null;

    public ManejadorCliente(Socket socket) {
        this.socket = socket;
    }
}