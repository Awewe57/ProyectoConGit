import java.io.*;
import java.net.*;

public class cliente {
    private static final String HOST = "localhost";
    private static final int PUERTO = 5000;

    public static void main(String[] args) {
        try (Socket socket = new Socket(HOST, PUERTO);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader teclado = new BufferedReader(new InputStreamReader(System.in))) {

            String respuesta;
            while ((respuesta = in.readLine()) != null) {
                System.out.println("Servidor: " + respuesta);

                if (respuesta.trim().endsWith(":") || respuesta.contains("Elige") || respuesta.contains("mensaje")) {
                    String entrada = teclado.readLine();
                    out.println(entrada);
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}