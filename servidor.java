import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class servidor {
    private static final int PUERTO = 5000;
    private static final String USUARIOS_FILE = "usuario.txt";
    private static final String GRUPOS_FILE = "grupos.txt";
    private static final String RANKING_FILE = "ranking.txt";
    private static final String MENSAJES_PRIVADOS_DIR = "mensajes_privados";
    private static final String MENSAJES_GRUPO_PREFIX = "mensajes_";
    private static final String VISTOS_FILE = "vistos.txt";

    private static final ConcurrentMap<String, ClienteHandler> clientes = new ConcurrentHashMap<>();
    private static final ConcurrentMap<String, String> usuarios = new ConcurrentHashMap<>(); // user -> pass
    private static final ConcurrentMap<String, Set<String>> grupos = new ConcurrentHashMap<>();
    private static final ConcurrentMap<String, Set<String>> bloqueos = new ConcurrentHashMap<>();
    private static final ConcurrentMap<String, Integer> ranking = new ConcurrentHashMap<>();
    private static final ConcurrentMap<String, JuegoGato> juegosActivos = new ConcurrentHashMap<>();
    private static final ConcurrentMap<String, Map<String, Integer>> vistos = new ConcurrentHashMap<>(); // usuario -> (grupo -> lastLine)

    public static void main(String[] args) {
        new File(MENSAJES_PRIVADOS_DIR).mkdirs();
        grupos.putIfAbsent("Todos", ConcurrentHashMap.newKeySet());
        cargarUsuarios();
        cargarGrupos();
        cargarRanking();
        cargarVistos();

        try (ServerSocket ss = new ServerSocket(PUERTO)) {
            System.out.println("Servidor iniciado en puerto " + PUERTO);
            while (true) {
                Socket s = ss.accept();
                new Thread(new ClienteHandler(s)).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static synchronized void cargarUsuarios() {
        File f = new File(USUARIOS_FILE);
        if (!f.exists()) return;
        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            String l;
            while ((l = br.readLine()) != null) {
                String[] p = l.split(",", 2);
                if (p.length == 2) usuarios.put(p[0], p[1]);
            }
        } catch (IOException ignored) {}
    }

    private static synchronized void guardarUsuario(String user, String pass) {
        usuarios.put(user, pass);
        try (PrintWriter pw = new PrintWriter(new FileWriter(USUARIOS_FILE, true))) {
            pw.println(user + "," + pass);
        } catch (IOException ignored) {}
    }

    private static synchronized void cargarGrupos() {
        File f = new File(GRUPOS_FILE);
        if (!f.exists()) return;
        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            String l;
            while ((l = br.readLine()) != null) {
                String[] parts = l.split(":", 2);
                if (parts.length == 2) {
                    String g = parts[0];
                    String[] members = parts[1].isEmpty() ? new String[0] : parts[1].split(",");
                    Set<String> set = ConcurrentHashMap.newKeySet();
                    for (String m : members) if (!m.isEmpty()) set.add(m.trim());
                    grupos.put(g, set);
                }
            }
        } catch (IOException ignored) {}
        grupos.putIfAbsent("Todos", ConcurrentHashMap.newKeySet());
    }

    private static synchronized void guardarGrupos() {
        try (PrintWriter pw = new PrintWriter(new FileWriter(GRUPOS_FILE))) {
            for (Map.Entry<String, Set<String>> e : grupos.entrySet()) {
                pw.println(e.getKey() + ":" + String.join(",", e.getValue()));
            }
        } catch (IOException ignored) {}
    }

    private static synchronized void cargarRanking() {
        File f = new File(RANKING_FILE);
        if (!f.exists()) return;
        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            String l;
            while ((l = br.readLine()) != null) {
                String[] p = l.split(":", 2);
                if (p.length == 2) {
                    try { ranking.put(p[0], Integer.parseInt(p[1])); } catch (NumberFormatException ignored) {}
                }
            }
        } catch (IOException ignored) {}
    }

    private static synchronized void guardarRanking() {
        try (PrintWriter pw = new PrintWriter(new FileWriter(RANKING_FILE))) {
            for (Map.Entry<String, Integer> e : ranking.entrySet()) pw.println(e.getKey() + ":" + e.getValue());
        } catch (IOException ignored) {}
    }

    private static synchronized void cargarVistos() {
        File f = new File(VISTOS_FILE);
        if (!f.exists()) return;
        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            String l;
            while ((l = br.readLine()) != null) {
                String[] p = l.split("\\|", 3);
                if (p.length == 3) {
                    vistos.putIfAbsent(p[0], new ConcurrentHashMap<>());
                    vistos.get(p[0]).put(p[1], Integer.parseInt(p[2]));
                }
            }
        } catch (IOException ignored) {}
    }

    private static synchronized void guardarVistos() {
        try (PrintWriter pw = new PrintWriter(new FileWriter(VISTOS_FILE))) {
            for (Map.Entry<String, Map<String, Integer>> e : vistos.entrySet()) {
                String user = e.getKey();
                for (Map.Entry<String, Integer> kv : e.getValue().entrySet()) {
                    pw.println(user + "|" + kv.getKey() + "|" + kv.getValue());
                }
            }
        } catch (IOException ignored) {}
    }

    static void enviarACliente(String user, String msg) {
        ClienteHandler ch = clientes.get(user);
        if (ch != null) ch.enviar(msg);
    }

    static void persistirMensajePrivado(String destinatario, String texto) {
        File f = new File(MENSAJES_PRIVADOS_DIR, destinatario + ".txt");
        try (FileWriter fw = new FileWriter(f, true)) {
            fw.write(texto + System.lineSeparator());
        } catch (IOException ignored) {}
    }

    static void enviarMensajeGrupo(String grupo, String remitente, String mensaje) {
        String fname = MENSAJES_GRUPO_PREFIX + grupo + ".txt";
        try (FileWriter fw = new FileWriter(fname, true)) {
            fw.write(remitente + ": " + mensaje + System.lineSeparator());
        } catch (IOException ignored) {}
        Set<String> members = grupos.getOrDefault(grupo, Collections.emptySet());
        for (String m : members) {
            Set<String> blocked = bloqueos.getOrDefault(m, Collections.emptySet());
            if (blocked.contains(remitente)) continue;
            enviarACliente(m, "[" + grupo + "] " + remitente + ": " + mensaje);
        }
    }

    static class ClienteHandler implements Runnable {
        private final Socket socket;
        private BufferedReader in;
        private PrintWriter out;
        private String nombre = null;
        private boolean invitado = false;
        private String grupoActual = "Todos";

        ClienteHandler(Socket s) { this.socket = s; }

        void enviar(String s) { out.println(s); }

        @Override
        public void run() {
            try {
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream(), true);

                autenticacion();
                clientes.put(nombre, this);
                grupos.putIfAbsent("Todos", ConcurrentHashMap.newKeySet());
                if (!invitado) { grupos.get("Todos").add(nombre); guardarGrupos(); }
                entregarMensajesPrivadosPendientes();
                mostrarMensajesNoLeidos(grupoActual);

                while (true) {
                    mostrarMenu();
                    String opcion = in.readLine();
                    if (opcion == null) break;
                    opcion = opcion.trim();
                    switch (opcion) {
                        case "1": listarGrupos(); break;
                        case "2": crearGrupo(); break;
                        case "3": unirseGrupo(); break;
                        case "4": salirGrupo(); break;
                        case "5": eliminarGrupo(); break;
                        case "6": enviarMensajeGrupo(); break;
                        case "7": listarUsuariosRegistrados(); break;
                        case "8": enviarMensajePrivado(); break;
                        case "9": bloquearUsuario(); break;
                        case "10": desbloquearUsuario(); break;
                        case "11": verBloqueados(); break;
                        case "12": proponerGato(); break;
                        case "13": jugarGatoMenu(); break;
                        case "14": verRankingGeneral(); break;
                        case "15": verRankingEntre(); break;
                        case "16": listarArchivosUsuario(); break;
                        case "17": descargarArchivoUsuario(); break;
                        case "18": salir(); return;
                        default: enviar("Opción no válida."); break;
                    }
                }
            } catch (IOException e) {
            } finally {
                clientes.remove(nombre);
                if (!invitado) {
                    for (Set<String> s : grupos.values()) s.remove(nombre);
                    guardarGrupos();
                }
                guardarVistos();
                try { socket.close(); } catch (IOException ignored) {}
            }
        }

        private void autenticacion() throws IOException {
            enviar("¿Tienes cuenta? (s/n)");
            String resp = in.readLine();
            if ("s".equalsIgnoreCase(resp)) {
                while (true) {
                    enviar("Usuario:");
                    String u = in.readLine();
                    enviar("Contraseña:");
                    String p = in.readLine();
                    if (u == null || p == null) throw new IOException("Cliente cerrado");
                    String stored = usuarios.get(u);
                    if (stored != null && stored.equals(p)) {
                        nombre = u;
                        invitado = false;
                        enviar("Inicio de sesión correcto. Bienvenido " + nombre);
                        return;
                    } else {
                        enviar("Usuario/contraseña incorrectos. Intentar de nuevo o escribir 'cancel' para registrarte.");
                        if ("cancel".equalsIgnoreCase(u)) break;
                    }
                }
            }

            enviar("Registro - escribe nuevo usuario:");
            String nu = in.readLine();
            enviar("Contraseña nueva:");
            String np = in.readLine();
            if (nu == null || np == null) throw new IOException("Cliente cerrado");
            guardarUsuario(nu, np);
            nombre = nu;
            invitado = false;
            enviar("Registrado y conectado como " + nombre);
        }


        private void mostrarMenu() { 
            enviar("\n--- MENÚ (usuario: " + nombre + ") ---");
            enviar("1  Ver grupos");
            enviar("2  Crear grupo");
            enviar("3  Unirse a grupo");
            enviar("4  Salir de grupo");
            enviar("5  Eliminar grupo");
            enviar("6  Enviar mensaje al grupo actual (" + grupoActual + ")");
            enviar("7  Listar usuarios registrados");
            enviar("8  Enviar mensaje privado");
            enviar("9  Bloquear usuario");
            enviar("10 Desbloquear usuario");
            enviar("11 Ver mis bloqueados");
            enviar("12 Proponer jugar al gato");
            enviar("13 Mover en gato / gestionar partidas");
            enviar("14 Ver ranking general");
            enviar("15 Ver situación entre dos jugadores");
            enviar("16 Listar archivos de otro usuario");
            enviar("17 Descargar archivo de otro usuario");
            enviar("18 Salir");
            enviar("Elige opción:");
        }


        private void listarGrupos() { enviar("Grupos: " + String.join(", ", grupos.keySet())); }

        private void crearGrupo() throws IOException {
            if (invitado) { enviar("Invitados no pueden crear grupos."); return; }
            enviar("Nombre del nuevo grupo:");
            String g = in.readLine().trim();
            if (g.isEmpty()) { enviar("Nombre vacío."); return; }
            grupos.putIfAbsent(g, ConcurrentHashMap.newKeySet());
            grupos.get(g).add(nombre);
            grupoActual = g;
            guardarGrupos();
            enviar("Grupo '" + g + "' creado y te has unido.");
        }

        private void unirseGrupo() throws IOException {
            enviar("¿A qué grupo deseas unirte?");
            String g = in.readLine().trim();
            if (!grupos.containsKey(g)) { enviar("Grupo no existe."); return; }
            if (invitado && !g.equals("Todos")) { enviar("Invitados solo pueden unirse a 'Todos'."); return; }
            grupos.get(g).add(nombre);
            grupoActual = g;
            guardarGrupos();
            enviar("Te uniste al grupo '" + g + "'. Mostrando mensajes no leídos...");
            mostrarMensajesNoLeidos(g);
        }

        private void salirGrupo() throws IOException {
            enviar("¿De qué grupo deseas salir?");
            String g = in.readLine().trim();
            if (g.equals("Todos")) { enviar("No puedes salir de 'Todos'."); return; }
            Set<String> s = grupos.get(g);
            if (s != null && s.remove(nombre)) {
                if (grupoActual.equals(g)) grupoActual = "Todos";
                guardarGrupos();
                enviar("Saliste del grupo '" + g + "'.");
            } else enviar("No perteneces a ese grupo.");
        }

        private void eliminarGrupo() throws IOException {
            enviar("¿Qué grupo deseas eliminar?");
            String g = in.readLine().trim();
            if (g.equals("Todos")) { enviar("No puedes eliminar 'Todos'."); return; }
            grupos.remove(g);
            new File(MENSAJES_GRUPO_PREFIX + g + ".txt").delete();
            guardarGrupos();
            enviar("Grupo '" + g + "' eliminado.");
        }

        private void mostrarMensajesNoLeidos(String grupo) {
            String file = MENSAJES_GRUPO_PREFIX + grupo + ".txt";
            int ultima = vistos.getOrDefault(nombre, Collections.emptyMap()).getOrDefault(grupo, 0);
            int contador = 0;
            File f = new File(file);
            if (!f.exists()) { enviar("No hay mensajes en " + grupo); return; }
            try (BufferedReader br = new BufferedReader(new FileReader(f))) {
                String line;
                while ((line = br.readLine()) != null) {
                    contador++;
                    if (contador > ultima) enviar(line);
                }
                vistos.putIfAbsent(nombre, new ConcurrentHashMap<>());
                vistos.get(nombre).put(grupo, contador);
                guardarVistos();
            } catch (IOException ignored) {}
        }


        private void enviarMensajeGrupo() throws IOException {
            if (invitado) { enviar("Invitados no pueden enviar mensajes."); return; }
            enviar("Escribe tu mensaje para el grupo '" + grupoActual + "':");
            String m = in.readLine();
            if (m == null) return;
            servidor.enviarMensajeGrupo(grupoActual, nombre, m);
            enviar("Mensaje enviado.");
        }


        private void listarUsuariosRegistrados() {
            enviar("Usuarios registrados:");
            for (String u : usuarios.keySet()) enviar("- " + u + (clientes.containsKey(u) ? " (conectado)" : " (desconectado)"));
        }

        private void enviarMensajePrivado() throws IOException {
            enviar("Selecciona usuario destino:");
            for (String u : usuarios.keySet()) enviar("- " + u + (clientes.containsKey(u) ? " (conectado)" : " (desconectado)"));
            String dest = in.readLine().trim();
            if (!usuarios.containsKey(dest)) { enviar("Usuario no existe."); return; }
            Set<String> bloqueadoPorDest = bloqueos.getOrDefault(dest, Collections.emptySet());
            if (bloqueadoPorDest.contains(nombre)) { enviar("No puedes enviar: te tiene bloqueado."); return; }
            enviar("Escribe mensaje privado:");
            String msg = in.readLine();
            String texto = nombre + ": " + msg;
            if (clientes.containsKey(dest)) {
                enviarACliente(dest, "(Privado) " + texto);
            } else {
                persistirMensajePrivado(dest, texto);
            }
            enviar("Mensaje enviado (se guardará si destinatario desconectado).");
        }

        private void entregarMensajesPrivadosPendientes() {
            File f = new File(MENSAJES_PRIVADOS_DIR, nombre + ".txt");
            if (!f.exists()) return;
            try (BufferedReader br = new BufferedReader(new FileReader(f))) {
                enviar("--- Mensajes pendientes ---");
                String l;
                while ((l = br.readLine()) != null) enviar(l);
            } catch (IOException ignored) {}
            f.delete();
        }


        private void bloquearUsuario() throws IOException {
            enviar("¿A quién deseas bloquear?");
            String u = in.readLine().trim();
            if (!usuarios.containsKey(u)) { enviar("Usuario no registrado."); return; }
            bloqueos.putIfAbsent(nombre, ConcurrentHashMap.newKeySet());
            bloqueos.get(nombre).add(u);
            enviar("Bloqueado: " + u);
        }

        private void desbloquearUsuario() throws IOException {
            Set<String> list = bloqueos.getOrDefault(nombre, Collections.emptySet());
            if (list.isEmpty()) { enviar("No tienes bloqueados."); return; }
            enviar("Bloqueados: " + String.join(", ", list));
            enviar("¿A quién deseas desbloquear?");
            String u = in.readLine().trim();
            list.remove(u);
            enviar("Desbloqueado: " + u);
        }

        private void verBloqueados() {
            Set<String> list = bloqueos.getOrDefault(nombre, Collections.emptySet());
            if (list.isEmpty()) enviar("No tienes bloqueados.");
            else {
                enviar("Bloqueados:");
                for (String s : list) enviar("- " + s);
            }
        }


        private void proponerGato() throws IOException {
            enviar("Usuarios conectados: " + clientes.keySet());
            enviar("¿A quién propones jugar?");
            String rival = in.readLine().trim();
            if (!clientes.containsKey(rival)) { enviar("Jugador no conectado."); return; }
            String key = clavePareja(nombre, rival);
            if (juegosActivos.containsKey(key)) { enviar("Ya existe una partida entre ustedes."); return; }
            enviarACliente(rival, "INVITACION_GATO de " + nombre + ". Para aceptar: ve a '13' y escribe 'ACEPTAR " + nombre + "'");
            enviar("Invitación enviada a " + rival);
        }

        private void jugarGatoMenu() throws IOException {
            enviar("Escribe: 'ACEPTAR <usuario>' para aceptar invitación, 'RECHAZAR <usuario>' para rechazar, 'MOVER <pos>' para mover (si estás en partida), 'ABANDONAR' para rendirte.");
            String linea = in.readLine();
            if (linea == null) return;
            String[] p = linea.split("\\s+");
            if (p.length == 0) return;
            String cmd = p[0].toUpperCase();
            if (cmd.equals("ACEPTAR") && p.length >= 2) {
                String proponente = p[1];
                String key = clavePareja(proponente, nombre);
                if (juegosActivos.containsKey(key)) { enviar("Ya hay una partida con ese usuario."); return; }
                JuegoGato juego = new JuegoGato(proponente, nombre);
                juegosActivos.put(key, juego);
                String primero = new Random().nextBoolean() ? proponente : nombre;
                enviarACliente(proponente, "Partida iniciada contra " + nombre + ". Empieza: " + primero);
                enviar("Partida iniciada contra " + proponente + ". Empieza: " + primero);
                juego.iniciar(primero);
            } else if (cmd.equals("RECHAZAR") && p.length >= 2) {
                String prop = p[1];
                enviar("Has rechazado invitación de " + prop);
                enviarACliente(prop, nombre + " rechazó tu invitación.");
            } else if (cmd.equals("MOVER") && p.length >= 2) {
                int pos;
                try { pos = Integer.parseInt(p[1]); } catch (NumberFormatException e) { enviar("Posición inválida."); return; }
                JuegoGato jg = null; String k = null;
                for (Map.Entry<String, JuegoGato> e : juegosActivos.entrySet()) {
                    if (e.getValue().involucra(nombre)) { jg = e.getValue(); k = e.getKey(); break; }
                }
                if (jg == null) { enviar("No estás en ninguna partida."); return; }
                String res = jg.mover(nombre, pos);
                if (res != null) enviar(res);
                if (jg.finalizada && k != null) juegosActivos.remove(k);
            } else if (cmd.equals("ABANDONAR")) {
                JuegoGato jg = null; String k = null;
                for (Map.Entry<String, JuegoGato> e : juegosActivos.entrySet()) {
                    if (e.getValue().involucra(nombre)) { jg = e.getValue(); k = e.getKey(); break; }
                }
                if (jg == null) { enviar("No estás en partida."); return; }
                String opp = jg.getOponente(nombre);
                enviarACliente(opp, nombre + " se rindió. Ganador: " + opp);
                ranking.put(opp, ranking.getOrDefault(opp, 0) + 2);
                guardarRanking();
                juegosActivos.remove(k);
                enviar("Te rendiste. Pierdes la partida.");
            } else {
                enviar("Comando de gato inválido.");
            }
        }

        private String clavePareja(String a, String b) {
            return a.compareTo(b) < 0 ? a + "_" + b : b + "_" + a;
        }

        private void verRankingGeneral() { 
            List<Map.Entry<String,Integer>> list = new ArrayList<>(ranking.entrySet());
            list.sort((a,b) -> b.getValue().compareTo(a.getValue()));
            enviar("=== Ranking ===");
            for (Map.Entry<String,Integer> e : list) enviar(e.getKey() + ": " + e.getValue() + " pts");
        }

        private void verRankingEntre() throws IOException {
            enviar("Jugador A:");
            String a = in.readLine().trim();
            enviar("Jugador B:");
            String b = in.readLine().trim();
            int pa = ranking.getOrDefault(a, 0), pb = ranking.getOrDefault(b, 0);
            int total = pa + pb;
            if (total == 0) enviar("Sin datos entre ellos.");
            else {
                enviar(a + ": " + pa + " pts, " + b + ": " + pb + " pts");
                enviar(String.format("%s %.1f%% - %s %.1f%%", a, pa*100.0/total, b, pb*100.0/total));
            }
        }


        private void listarArchivosUsuario() throws IOException {
            enviar("¿De qué usuario quieres listar archivos?");
            String u = in.readLine().trim();
            File dir = new File(".");
            File[] files = dir.listFiles((d,n)-> n.endsWith(".txt") && n.contains(u));
            if (files == null || files.length == 0) { enviar("No hay archivos encontrados."); return; }
            enviar("Archivos:");
            for (File f : files) enviar("- " + f.getName());
        }

        private void descargarArchivoUsuario() throws IOException {
            enviar("Nombre del archivo a descargar:");
            String name = in.readLine().trim();
            File f = new File(name);
            if (!f.exists()) { enviar("Archivo no encontrado."); return; }
            enviar("INICIO_ARCHIVO");
            try (BufferedReader br = new BufferedReader(new FileReader(f))) {
                String l;
                while ((l = br.readLine()) != null) enviar(l);
            } catch (IOException e) { enviar("ERROR leyendo archivo."); }
            enviar("FIN_ARCHIVO");
        }

        private void salir() throws IOException {
            enviar("Cerrando sesión...");
            socket.close();
        }
    }


    static class JuegoGato {
        private final String a, b;
        private final char[] board = new char[9];
        private String turno;
        boolean finalizada = false;

        JuegoGato(String a, String b) {
            this.a = a; this.b = b;
            Arrays.fill(board, ' ');
        }

        void iniciar(String primero) {
            this.turno = primero;
            enviarEstado();
            enviarACliente(turno, "Es tu turno.");
        }

        boolean involucra(String user) { return a.equals(user) || b.equals(user); }

        String getOponente(String user) { return a.equals(user) ? b : a; }

        synchronized String mover(String jugador, int pos) {
            if (finalizada) return "Partida finalizada.";
            if (!turno.equals(jugador)) return "No es tu turno.";
            if (pos < 1 || pos > 9) return "Posición inválida 1..9.";
            if (board[pos-1] != ' ') return "Casilla ocupada.";
            board[pos-1] = jugador.equals(a) ? 'X' : 'O';
            enviarEstado();
            String ganador = comprobarGanador();
            if (ganador != null) {
                finalizada = true;
                ranking.put(ganador, ranking.getOrDefault(ganador, 0) + 2);
                String otro = getOponente(ganador);
                enviarACliente(ganador, "GANASTE la partida!");
                enviarACliente(otro, "PERDISTE la partida. Ganador: " + ganador);
                guardarRanking();
                return null;
            }
            if (tableroLleno()) {
                finalizada = true;
                ranking.put(a, ranking.getOrDefault(a, 0) + 1);
                ranking.put(b, ranking.getOrDefault(b, 0) + 1);
                enviarACliente(a, "EMPATE");
                enviarACliente(b, "EMPATE");
                guardarRanking();
                return null;
            }
            turno = turno.equals(a) ? b : a;
            enviarACliente(turno, "Es tu turno.");
            return null;
        }

        private boolean tableroLleno() {
            for (char c : board) if (c == ' ') return false;
            return true;
        }

        private String comprobarGanador() {
            int[][] combos = {{0,1,2},{3,4,5},{6,7,8},{0,3,6},{1,4,7},{2,5,8},{0,4,8},{2,4,6}};
            for (int[] t : combos) {
                char c = board[t[0]];
                if (c != ' ' && board[t[1]] == c && board[t[2]] == c) {
                    return c == 'X' ? a : b;
                }
            }
            return null;
        }

        private void enviarEstado() {
            StringBuilder sb = new StringBuilder();
            sb.append("\nTablero:\n");
            for (int i = 0; i < 9; i++) {
                sb.append('[').append(board[i] == ' ' ? (i+1) : board[i]).append(']');
                if ((i+1) % 3 == 0) sb.append('\n');
            }
            enviarACliente(a, sb.toString());
            enviarACliente(b, sb.toString());
        }
    }
}