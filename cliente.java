import java.io.*;
import java.net.*;

public class cliente {
    private static final String HOST = "localhost";
    private static final int PUERTO = 12345;

    public static void main(String[] args) {
        try (Socket socket = new Socket(HOST, PUERTO);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader teclado = new BufferedReader(new InputStreamReader(System.in))) {

            Thread receptor = new Thread(() -> {
                try {
                    String mensaje;
                    while ((mensaje = in.readLine()) != null) {
                        System.out.println(mensaje);
                    }
                } catch (IOException e) {
                    System.out.println("Conexión cerrada.");
                }
            });
            receptor.start();

            String entrada;
            while ((entrada = teclado.readLine()) != null) {
                out.println(entrada);
                if (entrada.equalsIgnoreCase("/salir")) break;
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}