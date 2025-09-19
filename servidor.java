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

private boolean autenticar(String user, String pass) {
        try (BufferedReader br = new BufferedReader(new FileReader("usuario.txt"))) {
            String linea;
            while ((linea = br.readLine()) != null) {
                String[] partes = linea.split(",");
                if (partes.length == 2) {
                    if (partes[0].equals(user) && partes[1].equals(pass)) {
                        return true;
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }

private void guardarMensaje(String mensaje) {
        try (FileWriter fw = new FileWriter("mensajes_" + usuarioLogueado + ".txt", true)) {
            fw.write(mensaje + "\n");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
private List<String> leerMensajes() {
        List<String> mensajes = new ArrayList<>();
        File archivo = new File("mensajes_" + usuarioLogueado + ".txt");
        if (!archivo.exists()) return mensajes;

        try (BufferedReader br = new BufferedReader(new FileReader(archivo))) {
            String linea;
            while ((linea = br.readLine()) != null) {
                mensajes.add(linea);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return mensajes;
    }

    private void borrarMensajes() {
        String archivo = "mensajes_" + usuarioLogueado + ".txt";
        File f = new File(archivo);
        if (f.exists()) {
            f.delete();
            System.out.println("Todos los mensajes de " + usuarioLogueado + " borrados.");
        }
    }

    @Override
    public void run() {
        try {
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);

            out.println("Bienvenido. Ingrese usuario:");
            String user = in.readLine();
            out.println("Ingrese contraseña:");
            String pass = in.readLine();

            if (autenticar(user, pass)) {
                usuarioLogueado = user;
                out.println("Login exitoso. Bienvenido " + usuarioLogueado);

                String opcion;
                do {
                    out.println("Elige opción: 1) Mandar mensaje 2) Ver mensajes 3) Salir");
                    opcion = in.readLine();

                    switch (opcion) {
                        case "1":
                            out.println("Escribe tu mensaje:");
                            String mensaje = in.readLine();
                            guardarMensaje(mensaje);
                            out.println("Mensaje guardado.");
                            break;
                        case "2":
                            List<String> mensajes = leerMensajes();
                            out.println("Tus mensajes:");
                            for (String m : mensajes) {
                                out.println("- " + m);
                            }
                            break;
                        case "3":
                            out.println("Adiós!");
                            break;
                        default:
                            out.println("Opción no válida.");
                    }
                } while (!opcion.equals("3"));

            } else {
                out.println("Usuario o contraseña incorrectos.");
            }

            socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}