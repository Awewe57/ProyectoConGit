import java.io.*;
import java.net.*;
import java.util.Scanner;

public class cliente {
    private static final String HOST = "localhost";
    private static final int PUERTO = 5000;

    public static void main(String[] args) {
        try (Socket socket = new Socket(HOST, PUERTO);
             BufferedReader servidorIn = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter servidorOut = new PrintWriter(socket.getOutputStream(), true);
             Scanner sc = new Scanner(System.in)) {

            Thread lector = new Thread(() -> {
                try {
                    String linea;
                    while ((linea = servidorIn.readLine()) != null) {
                        if (linea.equals("INICIO_ARCHIVO")) {
                            File outFile = new File("archivo_descargado_" + System.currentTimeMillis() + ".txt");
                            try (PrintWriter pw = new PrintWriter(new FileWriter(outFile))) {
                                while (!(linea = servidorIn.readLine()).equals("FIN_ARCHIVO")) {
                                    pw.println(linea);
                                }
                                System.out.println("[Archivo guardado: " + outFile.getAbsolutePath() + "]");
                            } catch (IOException e) {
                                System.out.println("Error al guardar archivo.");
                            }
                            continue;
                        }
                        System.out.println(linea);
                    }
                } catch (IOException e) {
                    System.out.println("Conexión cerrada.");
                }
            });
            lector.setDaemon(true);
            lector.start();

            while (true) {
                String entrada = sc.nextLine();
                servidorOut.println(entrada);
                if ("18".equals(entrada)) break;
            }
        } catch (IOException e) {
            System.out.println("No se pudo conectar: " + e.getMessage());
        }
    }
}