import java.io.*;
import java.net.*;
import java.util.*;

public class servidor {
    private static final int PUERTO = 12345;
    private static final String ARCHIVO_USUARIOS = "usuario.txt";
    private static final String ARCHIVO_MENSAJES = "mensajes.txt";
    static Set<ManejadorCliente> clientesConectados = Collections.synchronizedSet(new HashSet<>());

    public static void main(String[] args) {
        try (ServerSocket servidor = new ServerSocket(PUERTO)) {
            System.out.println("Servidor iniciado en el puerto " + PUERTO);

            while (true) {
                Socket socket = servidor.accept();
                ManejadorCliente cliente = new ManejadorCliente(socket);
                clientesConectados.add(cliente);
                new Thread(cliente).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void broadcast(String mensaje, ManejadorCliente remitente) {
        synchronized (clientesConectados) {
            for (ManejadorCliente cliente : clientesConectados) {
                if (cliente != remitente) {
                    cliente.enviarMensaje(mensaje);
                }
            }
        }
    }

    public static void guardarMensaje(String mensaje) {
        try (FileWriter fw = new FileWriter(ARCHIVO_MENSAJES, true)) {
            fw.write(mensaje + "\n");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static boolean validarUsuario(String usuario, String contraseña) {
        try (BufferedReader br = new BufferedReader(new FileReader(ARCHIVO_USUARIOS))) {
            String linea;
            while ((linea = br.readLine()) != null) {
                String[] datos = linea.split(",");
                if (datos.length == 2 && datos[0].equals(usuario) && datos[1].equals(contraseña)) {
                    return true;
                }
            }
        } catch (IOException e) {
            
        }
        return false;
    }

    public static boolean registrarUsuario(String usuario, String contraseña) {
        if (usuario.trim().isEmpty() || contraseña.trim().isEmpty()) return false;
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(ARCHIVO_USUARIOS, true))) {
            bw.write(usuario + "," + contraseña);
            bw.newLine();
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}

class ManejadorCliente implements Runnable {
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private String nombreUsuario = "Visitante";
    private boolean autenticado = false;
    private int mensajesEnviados = 0;
    private Set<String> bloqueados = new HashSet<>();

    public ManejadorCliente(Socket socket) {
        this.socket = socket;
    }

    public void enviarMensaje(String mensaje) {
        out.println(mensaje);
    }

    @Override
    public void run() {
        try {
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            out.println("Bienvenido al chat!");
            out.println("Estás en modo VISITANTE hasta iniciar sesión o registrarte.");
            out.println("Comandos disponibles:");
            out.println("/registrar, /login, /listar, /bloquear, /desbloquear, /enviar, /verarchivos, /descargar, /salir");

            String mensaje;
            while ((mensaje = in.readLine()) != null) {
                if (mensaje.equalsIgnoreCase("/salir")) {
                    out.println("Desconectado del servidor.");
                    break;
                } else if (mensaje.equalsIgnoreCase("/registrar")) {
                    registrar();
                } else if (mensaje.equalsIgnoreCase("/login")) {
                    login();
                } else if (mensaje.equalsIgnoreCase("/listar")) {
                    listarUsuarios();
                } else if (mensaje.equalsIgnoreCase("/bloquear")) {
                    bloquearUsuario();
                } else if (mensaje.equalsIgnoreCase("/desbloquear")) {
                    desbloquearUsuario();
                } else if (mensaje.equalsIgnoreCase("/enviar")) {
                    enviarMensajeAUsuario();
                } else if (mensaje.equalsIgnoreCase("/verarchivos")) {
                    verArchivosDeUsuario();
                } else if (mensaje.equalsIgnoreCase("/descargar")) {
                    descargarArchivoDeUsuario();
                } else {
                    procesarMensajeGeneral(mensaje);
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try { socket.close(); } catch (IOException e) {}
            servidor.clientesConectados.remove(this);
        }
    }

    private void procesarMensajeGeneral(String mensaje) {
        if (!autenticado && mensajesEnviados >= 3) {
            out.println("⚠️ Has alcanzado el límite de 3 mensajes. Usa /login o /registrar para continuar.");
            return;
        }

        String textoFinal = nombreUsuario + ": " + mensaje;
        servidor.guardarMensaje(textoFinal);
        servidor.broadcast(textoFinal, this);

        mensajesEnviados++;
    }

    private void registrar() throws IOException {
        out.println("Ingresa nuevo nombre de usuario:");
        String usuario = in.readLine();
        out.println("Ingresa nueva contraseña:");
        String pass = in.readLine();

        if (servidor.registrarUsuario(usuario, pass)) {
            nombreUsuario = usuario;
            autenticado = true;
            out.println("✅ Usuario registrado exitosamente.");
        } else {
            out.println("❌ Error al registrar usuario.");
        }
    }

    private void login() throws IOException {
        out.println("Usuario:");
        String usuario = in.readLine();
        out.println("Contraseña:");
        String pass = in.readLine();

        if (servidor.validarUsuario(usuario, pass)) {
            nombreUsuario = usuario;
            autenticado = true;
            out.println("✅ Sesión iniciada correctamente.");
        } else {
            out.println("❌ Usuario o contraseña incorrectos.");
        }
    }

    private void listarUsuarios() {
        out.println("Usuarios conectados:");
        synchronized (servidor.clientesConectados) {
            for (ManejadorCliente cliente : servidor.clientesConectados) {
                out.println(" - " + cliente.nombreUsuario);
            }
        }
    }

    private void bloquearUsuario() throws IOException {
        List<String> disponibles = new ArrayList<>();
        synchronized (servidor.clientesConectados) {
            for (ManejadorCliente c : servidor.clientesConectados) {
                if (!c.nombreUsuario.equals(this.nombreUsuario) && !bloqueados.contains(c.nombreUsuario))
                    disponibles.add(c.nombreUsuario);
            }
        }

        if (disponibles.isEmpty()) {
            out.println("No hay usuarios disponibles para bloquear.");
            return;
        }

        out.println("Usuarios disponibles para bloquear:");
        for (String u : disponibles) out.println(" - " + u);
        out.println("Escribe el nombre del usuario que deseas bloquear:");
        String elegido = in.readLine();
        if (disponibles.contains(elegido)) {
            bloqueados.add(elegido);
            out.println("Usuario bloqueado: " + elegido);
        } else {
            out.println("Nombre inválido.");
        }
    }

    private void desbloquearUsuario() throws IOException {
        if (bloqueados.isEmpty()) {
            out.println("No tienes usuarios bloqueados.");
            return;
        }
        out.println("Usuarios bloqueados:");
        for (String u : bloqueados) out.println(" - " + u);
        out.println("Escribe el nombre del usuario que deseas desbloquear:");
        String elegido = in.readLine();
        if (bloqueados.remove(elegido)) {
            out.println("Usuario desbloqueado: " + elegido);
        } else {
            out.println("Nombre inválido.");
        }
    }

    private void enviarMensajeAUsuario() throws IOException {
        out.println("Usuarios conectados:");
        List<ManejadorCliente> lista = new ArrayList<>();
        synchronized (servidor.clientesConectados) {
            for (ManejadorCliente c : servidor.clientesConectados) {
                if (!c.nombreUsuario.equals(this.nombreUsuario)) {
                    out.println(" - " + c.nombreUsuario);
                    lista.add(c);
                }
            }
        }
        out.println("¿A quién deseas enviar mensaje?");
        String destino = in.readLine();
        out.println("Escribe tu mensaje:");
        String texto = in.readLine();

        for (ManejadorCliente c : lista) {
            if (c.nombreUsuario.equals(destino) && !c.bloqueados.contains(this.nombreUsuario)) {
                c.enviarMensaje("📩 Mensaje privado de " + nombreUsuario + ": " + texto);
                servidor.guardarMensaje("[Privado] " + nombreUsuario + " -> " + destino + ": " + texto);
                out.println("Mensaje enviado a " + destino);
                return;
            }
        }
        out.println("No se pudo enviar mensaje (usuario no encontrado o te bloqueó).");
    }

    private void verArchivosDeUsuario() throws IOException {
        out.println("Usuarios conectados:");
        List<ManejadorCliente> lista = new ArrayList<>();
        synchronized (servidor.clientesConectados) {
            for (ManejadorCliente c : servidor.clientesConectados) {
                if (!c.nombreUsuario.equals(this.nombreUsuario)) {
                    out.println(" - " + c.nombreUsuario);
                    lista.add(c);
                }
            }
        }

        out.println("¿De quién quieres ver archivos?");
        String usuario = in.readLine();
        File carpeta = new File(".");
        File[] archivos = carpeta.listFiles((dir, name) -> name.endsWith(".txt"));
        out.println("Archivos .txt disponibles en " + usuario + ":");
        if (archivos != null) {
            for (File f : archivos) out.println(" - " + f.getName());
        }
    }

    private void descargarArchivoDeUsuario() throws IOException {
        out.println("Escribe el nombre del archivo .txt a descargar:");
        String nombreArchivo = in.readLine();
        File archivo = new File(nombreArchivo);
        if (!archivo.exists()) {
            out.println("Archivo no encontrado.");
            return;
        }

        try (BufferedReader br = new BufferedReader(new FileReader(archivo))) {
            out.println("Contenido de " + nombreArchivo + ":");
            String linea;
            while ((linea = br.readLine()) != null) {
                out.println(linea);
            }
        }
    }
}