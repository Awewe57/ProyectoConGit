import java.io.*;
import java.net.*;
import java.util.Scanner;


public class cliente {
    public static void main(String[] args) {
        String host = "localhost";
        int puerto = 5000;
        try (Socket s = new Socket(host, puerto);
             BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream()));
             PrintWriter out = new PrintWriter(s.getOutputStream(), true);
             Scanner sc = new Scanner(System.in)) {

            Thread t = new Thread(() -> {
                try {
                    String l;
                    while ((l = in.readLine()) != null) {
                        System.out.println(l);
                    }
                } catch (IOException e) {
                    System.out.println("Conexión cerrada.");
                }
            });
            t.setDaemon(true);
            t.start();

            while (true) {
                String entrada = sc.nextLine();
                out.println(entrada);
                if (entrada.equals("15")) break;
            }

        } catch (IOException e) {
            System.out.println("No se pudo conectar al servidor: " + e.getMessage());
        }
    }
}