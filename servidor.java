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

    private void guardarMensaje(String destinatario, String mensaje) {
        String archivo = "mensajes_" + destinatario + ".txt";
        try (FileWriter fw = new FileWriter(archivo, true)) {
            fw.write("De " + usuarioLogueado + ": " + mensaje + "\n");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private List<String> leerMensajes() {
        List<String> mensajes = new ArrayList<>();
        String archivo = "mensajes_" + usuarioLogueado + ".txt";
        File f = new File(archivo);
        if (!f.exists()) return mensajes;

        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
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
        File f = new File("mensajes_" + usuarioLogueado + ".txt");
        if (f.exists()) f.delete();
    }

    private void borrarUsuario() {
        File temp = new File("usuario_temp.txt");
        try (BufferedReader br = new BufferedReader(new FileReader("usuario.txt"));
             BufferedWriter bw = new BufferedWriter(new FileWriter(temp))) {

            String linea;
            while ((linea = br.readLine()) != null) {
                String[] partes = linea.split(",");
                if (!partes[0].equals(usuarioLogueado)) {
                    bw.write(linea + "\n");
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        new File("usuario.txt").delete();
        temp.renameTo(new File("usuario.txt"));

        borrarMensajes();
    }

    private List<String> listarUsuarios() {
        List<String> usuarios = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader("usuario.txt"))) {
            String linea;
            while ((linea = br.readLine()) != null) {
                String[] partes = linea.split(",");
                if (partes.length == 2) {
                    usuarios.add(partes[0]);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return usuarios;
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
                    out.println("Opciones: 1) Enviar mensaje 2) Ver mensajes 3) Borrar mensajes 4) Borrar usuario 5) Salir");
                    opcion = in.readLine();

                    switch (opcion) {
                        case "1":
                            List<String> usuarios = listarUsuarios();
                            usuarios.remove(usuarioLogueado);
                            if (usuarios.isEmpty()) {
                                out.println("No hay otros usuarios registrados para enviar mensajes.");
                                break;
                            }

                            out.println("Usuarios disponibles:");
                            for (int i = 0; i < usuarios.size(); i++) {
                                out.println((i + 1) + ") " + usuarios.get(i));
                            }
                            out.println("Elige el número del usuario destinatario:");
                            int idx;
                            try {
                                idx = Integer.parseInt(in.readLine()) - 1;
                            } catch (NumberFormatException e) {
                                out.println("Selección inválida.");
                                break;
                            }

                            if (idx < 0 || idx >= usuarios.size()) {
                                out.println("Selección inválida.");
                                break;
                            }

                            String destinatario = usuarios.get(idx);
                            out.println("Escribe tu mensaje:");
                            String mensaje = in.readLine();
                            guardarMensaje(destinatario, mensaje);
                            out.println("Mensaje enviado a " + destinatario);
                            break;

                        case "2":
                            List<String> mensajes = leerMensajes();
                            if (mensajes.isEmpty()) {
                                out.println("No tienes mensajes.");
                            } else {
                                out.println("Tus mensajes:");
                                for (String m : mensajes) {
                                    out.println("- " + m);
                                }
                            }
                            break;

                        case "3":
                            borrarMensajes();
                            out.println("Todos tus mensajes fueron borrados.");
                            break;

                        case "4":
                            borrarUsuario();
                            out.println("Tu usuario fue borrado. Adiós.");
                            opcion = "5"; // salir
                            break;

                        case "5":
                            out.println("Adiós!");
                            break;

                        default:
                            out.println("Opción no válida.");
                    }
                } while (!opcion.equals("5"));
            } else {
                out.println("Usuario o contraseña incorrectos.");
            }

            socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}