import java.io.*;
import java.net.*;
import java.util.*;

public class servidor {
    private static final int PUERTO = 5000;
    private static final String ARCHIVO_USUARIOS = "usuario.txt";
    private static final String CARPETA_MENSAJES = "mensajes";

    public static void main(String[] args) {
        try (ServerSocket serverSocket = new ServerSocket(PUERTO)) {
            System.out.println("Servidor iniciado en el puerto " + PUERTO);

            File carpetaMensajes = new File(CARPETA_MENSAJES);
            if (!carpetaMensajes.exists()) {
                carpetaMensajes.mkdir();
            }

            while (true) {
                Socket socket = serverSocket.accept();
                new Thread(new ClienteHandler(socket)).start();
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    static class ClienteHandler implements Runnable {
        private Socket socket;
        private String usuarioLogueado;

        ClienteHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try (
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                PrintWriter out = new PrintWriter(socket.getOutputStream(), true)
            ) {
                
                out.println("Usuario:");
                String usuario = in.readLine();
                out.println("Contraseña:");
                String password = in.readLine();

                if (!autenticarUsuario(usuario, password)) {
                    out.println("Login fallido.");
                    socket.close();
                    return;
                }

                usuarioLogueado = usuario;
                out.println("Login exitoso. Bienvenido " + usuarioLogueado);

                while (true) {
                    out.println("\n--- MENÚ ---");
                    out.println("1. Ver usuarios disponibles");
                    out.println("2. Mandar mensaje");
                    out.println("3. Ver mis mensajes");
                    out.println("4. Bloquear usuario");
                    out.println("5. Desbloquear usuario");
                    out.println("6. Salir");
                    out.println("7. Listar archivos de otro usuario");
                    out.println("8. Descargar archivo de otro usuario");
                    String opcion = in.readLine();

                    switch (opcion) {
                        case "1":
                            mostrarUsuarios(out);
                            break;
                        case "2":
                            enviarMensaje(in, out);
                            break;
                        case "3":
                            verMensajes(out);
                            break;
                        case "4":
                            bloquearUsuario(in, out);
                            break;
                        case "5":
                            desbloquearUsuario(in, out);
                            break;
                        case "6":
                            out.println("Cerrando sesión...");
                            socket.close();
                            return;
                        case "7":
                            listarArchivosOtroUsuario(in, out);
                            break;
                        case "8":
                            transferirArchivoOtroUsuario(in, out);
                            break;
                        default:
                            out.println("Opción inválida.");
                    }
                }

            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        private boolean autenticarUsuario(String usuario, String password) {
            try (BufferedReader br = new BufferedReader(new FileReader(ARCHIVO_USUARIOS))) {
                String linea;
                while ((linea = br.readLine()) != null) {
                    String[] partes = linea.split(",");
                    if (partes[0].equals(usuario) && partes[1].equals(password)) {
                        return true;
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            return false;
        }

        private void mostrarUsuarios(PrintWriter out) {
            List<String> usuarios = cargarUsuarios();
            out.println("\nUsuarios disponibles:");
            for (String u : usuarios) {
                if (!u.equals(usuarioLogueado)) {
                    out.println("- " + u);
                }
            }
        }

        private void enviarMensaje(BufferedReader in, PrintWriter out) throws IOException {
            List<String> usuarios = cargarUsuarios();
            usuarios.remove(usuarioLogueado);

            if (usuarios.isEmpty()) {
                out.println("No hay otros usuarios registrados.");
                return;
            }

            out.println("\nUsuarios disponibles para enviar mensaje:");
            for (int i = 0; i < usuarios.size(); i++) {
                out.println((i + 1) + ". " + usuarios.get(i));
            }
            out.println("Elige el número del usuario:");
            int idx = Integer.parseInt(in.readLine()) - 1;

            if (idx < 0 || idx >= usuarios.size()) {
                out.println("Selección inválida.");
                return;
            }

            String receptor = usuarios.get(idx);

            
            if (estaBloqueado(usuarioLogueado, receptor)) {
                out.println("No puedes enviar mensaje a " + receptor + " porque lo tienes bloqueado.");
                return;
            }
            if (estaBloqueado(receptor, usuarioLogueado)) {
                out.println("No puedes enviar mensaje a " + receptor + " porque te tiene bloqueado.");
                return;
            }

            out.println("Escribe tu mensaje:");
            String mensaje = in.readLine();

            guardarMensaje(receptor, "De " + usuarioLogueado + ": " + mensaje);
            out.println("Mensaje enviado a " + receptor);
        }

        private void verMensajes(PrintWriter out) {
            File archivo = new File(CARPETA_MENSAJES, "mensajes_" + usuarioLogueado + ".txt");
            if (!archivo.exists()) {
                out.println("No tienes mensajes.");
                return;
            }

            try (BufferedReader br = new BufferedReader(new FileReader(archivo))) {
                String linea;
                out.println("\n--- Tus mensajes ---");
                while ((linea = br.readLine()) != null) {
                    out.println(linea);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        private void bloquearUsuario(BufferedReader in, PrintWriter out) throws IOException {
            List<String> usuarios = cargarUsuarios();
            usuarios.remove(usuarioLogueado);

            List<String> bloqueados = cargarBloqueados(usuarioLogueado);
            usuarios.removeAll(bloqueados);

            if (usuarios.isEmpty()) {
                out.println("No hay usuarios para bloquear.");
                return;
            }

            out.println("\nUsuarios disponibles para bloquear:");
            for (int i = 0; i < usuarios.size(); i++) {
                out.println((i + 1) + ". " + usuarios.get(i));
            }
            out.println("Elige el número del usuario:");
            int idx = Integer.parseInt(in.readLine()) - 1;

            if (idx < 0 || idx >= usuarios.size()) {
                out.println("Selección inválida.");
                return;
            }

            String bloqueado = usuarios.get(idx);
            guardarBloqueado(usuarioLogueado, bloqueado);
            out.println("Has bloqueado a " + bloqueado);
        }

        private void desbloquearUsuario(BufferedReader in, PrintWriter out) throws IOException {
            List<String> bloqueados = cargarBloqueados(usuarioLogueado);

            if (bloqueados.isEmpty()) {
                out.println("No tienes usuarios bloqueados.");
                return;
            }

            out.println("\nUsuarios bloqueados:");
            for (int i = 0; i < bloqueados.size(); i++) {
                out.println((i + 1) + ". " + bloqueados.get(i));
            }
            out.println("Elige el número del usuario para desbloquear:");
            int idx = Integer.parseInt(in.readLine()) - 1;

            if (idx < 0 || idx >= bloqueados.size()) {
                out.println("Selección inválida.");
                return;
            }

            String desbloqueado = bloqueados.get(idx);
            eliminarBloqueado(usuarioLogueado, desbloqueado);
            out.println("Has desbloqueado a " + desbloqueado);
        }

        private void listarArchivosOtroUsuario(BufferedReader in, PrintWriter out) throws IOException {
            List<String> usuarios = cargarUsuarios();
            usuarios.remove(usuarioLogueado);

            if (usuarios.isEmpty()) {
                out.println("No hay otros usuarios disponibles.");
                return;
            }

            out.println("\nUsuarios disponibles para listar archivos:");
            for (int i = 0; i < usuarios.size(); i++) {
                out.println((i + 1) + ". " + usuarios.get(i));
            }
            out.println("Elige el número del usuario:");
            int idx = Integer.parseInt(in.readLine()) - 1;

            if (idx < 0 || idx >= usuarios.size()) {
                out.println("Selección inválida.");
                return;
            }

            String objetivo = usuarios.get(idx);
            File carpetaObjetivo = new File(".");
            File[] archivos = carpetaObjetivo.listFiles((dir, name) -> name.endsWith(".txt") && name.contains(objetivo));

            if (archivos == null || archivos.length == 0) {
                out.println("El usuario " + objetivo + " no tiene archivos .txt.");
                return;
            }

            out.println("\nArchivos .txt de " + objetivo + ":");
            for (int i = 0; i < archivos.length; i++) {
                out.println((i + 1) + ". " + archivos[i].getName());
            }
        }

        private void transferirArchivoOtroUsuario(BufferedReader in, PrintWriter out) throws IOException {
            List<String> usuarios = cargarUsuarios();
            usuarios.remove(usuarioLogueado);

            if (usuarios.isEmpty()) {
                out.println("No hay otros usuarios disponibles.");
                return;
            }

            out.println("\nUsuarios disponibles para descargar archivo:");
            for (int i = 0; i < usuarios.size(); i++) {
                out.println((i + 1) + ". " + usuarios.get(i));
            }
            out.println("Elige el número del usuario:");
            int idx = Integer.parseInt(in.readLine()) - 1;

            if (idx < 0 || idx >= usuarios.size()) {
                out.println("Selección inválida.");
                return;
            }

            String objetivo = usuarios.get(idx);
            File carpetaObjetivo = new File(".");
            File[] archivos = carpetaObjetivo.listFiles((dir, name) -> name.endsWith(".txt") && name.contains(objetivo));

            if (archivos == null || archivos.length == 0) {
                out.println("El usuario " + objetivo + " no tiene archivos .txt.");
                return;
            }

            out.println("\nArchivos .txt de " + objetivo + ":");
            for (int i = 0; i < archivos.length; i++) {
                out.println((i + 1) + ". " + archivos[i].getName());
            }
            out.println("Elige el número del archivo:");
            int idxArchivo = Integer.parseInt(in.readLine()) - 1;

            if (idxArchivo < 0 || idxArchivo >= archivos.length) {
                out.println("Selección inválida.");
                return;
            }

            File archivoSeleccionado = archivos[idxArchivo];
            out.println("INICIO_ARCHIVO");
            try (BufferedReader br = new BufferedReader(new FileReader(archivoSeleccionado))) {
                String linea;
                while ((linea = br.readLine()) != null) {
                    out.println(linea);
                }
            }
            out.println("FIN_ARCHIVO");
        }

        private List<String> cargarUsuarios() {
            List<String> usuarios = new ArrayList<>();
            try (BufferedReader br = new BufferedReader(new FileReader(ARCHIVO_USUARIOS))) {
                String linea;
                while ((linea = br.readLine()) != null) {
                    String[] partes = linea.split(",");
                    usuarios.add(partes[0]);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            return usuarios;
        }

        private void guardarMensaje(String receptor, String mensaje) {
            File archivo = new File(CARPETA_MENSAJES, "mensajes_" + receptor + ".txt");
            try (FileWriter fw = new FileWriter(archivo, true)) {
                fw.write(mensaje + "\n");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        private boolean estaBloqueado(String usuario, String objetivo) {
            List<String> bloqueados = cargarBloqueados(usuario);
            return bloqueados.contains(objetivo);
        }

        private List<String> cargarBloqueados(String usuario) {
            List<String> bloqueados = new ArrayList<>();
            File archivo = new File("bloqueados_" + usuario + ".txt");
            if (!archivo.exists()) {
                return bloqueados;
            }
            try (BufferedReader br = new BufferedReader(new FileReader(archivo))) {
                String linea;
                while ((linea = br.readLine()) != null) {
                    bloqueados.add(linea.trim());
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            return bloqueados;
        }

        private void guardarBloqueado(String usuario, String bloqueado) {
            File archivo = new File("bloqueados_" + usuario + ".txt");
            try (FileWriter fw = new FileWriter(archivo, true)) {
                fw.write(bloqueado + "\n");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        private void eliminarBloqueado(String usuario, String desbloqueado) {
            File archivo = new File("bloqueados_" + usuario + ".txt");
            List<String> bloqueados = cargarBloqueados(usuario);
            bloqueados.remove(desbloqueado);

            try (FileWriter fw = new FileWriter(archivo, false)) {
                for (String b : bloqueados) {
                    fw.write(b + "\n");
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}