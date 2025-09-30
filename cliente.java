import java.io.*;
import java.net.*;
import java.util.Scanner;

public class cliente {
    private static final String HOST = "localhost";
    private static final int PUERTO = 5000;

    public static void main(String[] args) {
        try (Socket socket = new Socket(HOST, PUERTO);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             Scanner sc = new Scanner(System.in)) {

    
            System.out.println(in.readLine());
            out.println(sc.nextLine());

            System.out.println(in.readLine());
            out.println(sc.nextLine());

            String respuesta = in.readLine();
            System.out.println(respuesta);

            if (respuesta.contains("fallido")) {
                return;
            }

            
            while (true) {
                String linea;
                while ((linea = in.readLine()) != null) {
                    if (linea.startsWith("--- MENÚ ---")) {
                        System.out.println(linea);
                        break;
                    }
                    System.out.println(linea);
                }

                
                while ((linea = in.readLine()) != null && !linea.isEmpty()) {
                    System.out.println(linea);
                    if (linea.contains("Salir")) {
                        break;
                    }
                }

                
                String opcion = sc.nextLine();
                out.println(opcion);

                
                while ((linea = in.readLine()) != null) {

                    
                    if (linea.equals("INICIO_ARCHIVO")) {
                        System.out.println("Recibiendo archivo...");
                        File archivoLocal = new File("archivo_descargado.txt");
                        try (PrintWriter pw = new PrintWriter(new FileWriter(archivoLocal))) {
                            while (!(linea = in.readLine()).equals("FIN_ARCHIVO")) {
                                pw.println(linea);
                            }
                        }
                        System.out.println("Archivo guardado en: " + archivoLocal.getAbsolutePath());
                        continue;
                    }

                    System.out.println(linea);

                    
                    if (linea.startsWith("--- MENÚ ---") || linea.contains("Cerrando sesión...")) {
                        break;
                    }

                    
                    if (linea.contains("Elige") || linea.contains("Escribe tu mensaje:")) {
                        String inputExtra = sc.nextLine();
                        out.println(inputExtra);
                    }
                }

                
                if (linea != null && linea.contains("Cerrando sesión...")) {
                    break;
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}