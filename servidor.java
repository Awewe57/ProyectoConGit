import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;


public class servidor {
    private static final int PUERTO = 5000;
    private static final String GRUPOS_FILE = "grupos.txt";
    private static final String RANKING_FILE = "ranking.txt";


    private static final ConcurrentMap<String, ClienteHandler> clientes = new ConcurrentHashMap<>();
    private static final ConcurrentMap<String, Set<String>> grupos = new ConcurrentHashMap<>();
    private static final ConcurrentMap<String, Set<String>> bloqueos = new ConcurrentHashMap<>();
    private static final ConcurrentMap<String, Integer> ranking = new ConcurrentHashMap<>();
    private static final ConcurrentMap<String, JuegoGato> juegosActivos = new ConcurrentHashMap<>();

    public static void main(String[] args) {
        grupos.putIfAbsent("Todos", ConcurrentHashMap.newKeySet());
        cargarGrupos();
        cargarRanking();

        try (ServerSocket ss = new ServerSocket(PUERTO)) {
            System.out.println("Servidor iniciado en puerto " + PUERTO);
            while (true) {
                Socket s = ss.accept();
                ClienteHandler h = new ClienteHandler(s);
                new Thread(h).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /* ------------------------ Persistencia ------------------------ */

    private static void cargarGrupos() {
        File f = new File(GRUPOS_FILE);
        if (!f.exists()) return;
        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            String linea;
            while ((linea = br.readLine()) != null) {
                String[] partes = linea.split(":", 2);
                if (partes.length == 2) {
                    String nombre = partes[0];
                    String miembros = partes[1];
                    String[] arr = miembros.isEmpty() ? new String[0] : miembros.split(",");
                    Set<String> set = ConcurrentHashMap.newKeySet();
                    for (String m : arr) if (!m.trim().isEmpty()) set.add(m.trim());
                    grupos.put(nombre, set);
                }
            }
        } catch (IOException ignored) {}
    }

    private static void guardarGrupos() {
        try (PrintWriter pw = new PrintWriter(new FileWriter(GRUPOS_FILE))) {
            for (Map.Entry<String, Set<String>> e : grupos.entrySet()) {
                pw.println(e.getKey() + ":" + String.join(",", e.getValue()));
            }
        } catch (IOException ignored) {}
    }

    private static void cargarRanking() {
        File f = new File(RANKING_FILE);
        if (!f.exists()) return;
        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            String linea;
            while ((linea = br.readLine()) != null) {
                String[] p = linea.split(":", 2);
                if (p.length == 2) {
                    try {
                        ranking.put(p[0], Integer.parseInt(p[1]));
                    } catch (NumberFormatException ignored) {}
                }
            }
        } catch (IOException ignored) {}
    }

    private static void guardarRanking() {
        try (PrintWriter pw = new PrintWriter(new FileWriter(RANKING_FILE))) {
            for (Map.Entry<String, Integer> e : ranking.entrySet()) {
                pw.println(e.getKey() + ":" + e.getValue());
            }
        } catch (IOException ignored) {}
    }

    /* ------------------------ Mensajería helpers ------------------------ */

    static void enviarACliente(String nombre, String texto) {
        ClienteHandler h = clientes.get(nombre);
        if (h != null) h.enviar(texto);
    }

    static void enviarAMiembrosGrupo(String grupo, String remitente, String mensaje) {
        String filename = "mensajes_" + grupo + ".txt";
        try (FileWriter fw = new FileWriter(filename, true)) {
            fw.write(remitente + ": " + mensaje + System.lineSeparator());
        } catch (IOException e) {
            e.printStackTrace();
        }

        Set<String> miembros = grupos.getOrDefault(grupo, Collections.emptySet());
        for (String miembro : miembros) {
            Set<String> bloqueadosPorMiembro = bloqueos.getOrDefault(miembro, Collections.emptySet());
            if (bloqueadosPorMiembro.contains(remitente)) continue;
            enviarACliente(miembro, "[" + grupo + "] " + remitente + ": " + mensaje);
        }
    }

    /* ------------------------ ClienteHandler ------------------------ */

    static class ClienteHandler implements Runnable {
        private final Socket socket;
        private BufferedReader in;
        private PrintWriter out;
        private String nombre = null;
        private boolean invitado = false;
        private String grupoActual = "Todos";

        ClienteHandler(Socket socket) {
            this.socket = socket;
        }

        void enviar(String texto) {
            out.println(texto);
        }

        @Override
        public void run() {
            try {
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream(), true);

                out.println("Bienvenido. Escribe tu nombre de usuario (o 'invitado'):");
                String linea = in.readLine();
                if (linea == null) { socket.close(); return; }
                nombre = linea.trim();
                if (nombre.equalsIgnoreCase("invitado")) {
                    invitado = true;
                    out.println("Modo INVITADO: lectura en 'Todos' permitida; crear/unirse a otros grupos y enviar mensajes restringidos.");
                } else {
                    grupos.putIfAbsent("Todos", ConcurrentHashMap.newKeySet());
                    grupos.get("Todos").add(nombre);
                    guardarGrupos();
                }

                clientes.put(nombre, this);
                enviar("Bienvenido " + nombre + ".");
                while (true) {
                    mostrarMenu();
                    String opt = in.readLine();
                    if (opt == null) break;
                    opt = opt.trim();
                    try {
                        procesarOpcion(opt);
                    } catch (Exception ex) {
                        out.println("Error procesando opción: " + ex.getMessage());
                        ex.printStackTrace();
                    }
                }
            } catch (IOException e) {
            } finally {
                clientes.remove(nombre);
                if (!invitado) {
                    for (Set<String> s : grupos.values()) s.remove(nombre);
                    guardarGrupos();
                }
                try { socket.close(); } catch (IOException ignored) {}
            }
        }

        private void mostrarMenu() {
            out.println("\n--- MENÚ --- (usuario: " + nombre + ")");
            out.println("1  Ver grupos");
            out.println("2  Crear grupo");
            out.println("3  Unirse a grupo");
            out.println("4  Salir de grupo");
            out.println("5  Eliminar grupo");
            out.println("6  Enviar mensaje al grupo actual (" + grupoActual + ")");
            out.println("7  Mensaje privado");
            out.println("8  Bloquear usuario");
            out.println("9  Desbloquear usuario");
            out.println("10 Jugar al gato (proponer)");
            out.println("11 Ver ranking general");
            out.println("12 Ver comparación entre dos jugadores");
            out.println("13 Listar archivos de otro usuario");
            out.println("14 Descargar archivo de otro usuario");
            out.println("15 Salir");
            out.print("Elige opción: ");
        }

        private void procesarOpcion(String opcion) throws IOException {
            switch (opcion) {
                case "1": listarGrupos(); break;
                case "2": crearGrupo(); break;
                case "3": unirseGrupo(); break;
                case "4": salirGrupo(); break;
                case "5": eliminarGrupo(); break;
                case "6": enviarMensajeGrupo(); break;
                case "7": mensajePrivado(); break;
                case "8": bloquear(); break;
                case "9": desbloquear(); break;
                case "10": proponerGato(); break;
                case "11": mostrarRankingGeneral(); break;
                case "12": compararRanking(); break;
                case "13": listarArchivosUsuario(); break;
                case "14": descargarArchivoUsuario(); break;
                case "15": out.println("Hasta luego."); socket.close(); break;
                default: out.println("Opción no válida."); break;
            }
        }

        /* ---------- grupos ---------- */

        private void listarGrupos() {
            out.println("Grupos: " + String.join(", ", grupos.keySet()));
        }

        private void crearGrupo() throws IOException {
            if (invitado) { out.println("Invitados no pueden crear grupos."); return; }
            out.println("Nombre del nuevo grupo:");
            String g = in.readLine().trim();
            if (g.isEmpty()) { out.println("Nombre vacío."); return; }
            grupos.putIfAbsent(g, ConcurrentHashMap.newKeySet());
            grupos.get(g).add(nombre);
            grupoActual = g;
            guardarGrupos();
            out.println("Grupo '" + g + "' creado y te has unido.");
        }

        private void unirseGrupo() throws IOException {
            out.println("¿A qué grupo deseas unirte?");
            String g = in.readLine().trim();
            if (g.equals("Todos") || grupos.containsKey(g)) {
                if (invitado && !g.equals("Todos")) { out.println("Invitados solo pueden unirse a 'Todos'."); return; }
                grupos.putIfAbsent(g, ConcurrentHashMap.newKeySet());
                grupos.get(g).add(nombre);
                grupoActual = g;
                guardarGrupos();
                out.println("Te uniste al grupo '" + g + "'. Mostrando mensajes no leídos...");
                mostrarMensajesNoLeidos(g);
            } else out.println("Grupo no existe.");
        }

        private void salirGrupo() throws IOException {
            out.println("¿De qué grupo quieres salir?");
            String g = in.readLine().trim();
            if (g.equals("Todos")) { out.println("No puedes salir de 'Todos'."); return; }
            Set<String> s = grupos.get(g);
            if (s != null && s.remove(nombre)) {
                if (grupoActual.equals(g)) grupoActual = "Todos";
                guardarGrupos();
                out.println("Saliste del grupo " + g);
            } else out.println("No perteneces a ese grupo.");
        }

        private void eliminarGrupo() throws IOException {
            out.println("¿Qué grupo deseas eliminar?");
            String g = in.readLine().trim();
            if (g.equals("Todos")) { out.println("No puedes eliminar 'Todos'."); return; }
            grupos.remove(g);
            File f = new File("mensajes_" + g + ".txt");
            if (f.exists()) f.delete();
            guardarGrupos();
            out.println("Grupo eliminado: " + g);
        }

        private void mostrarMensajesNoLeidos(String grupo) {
            String filename = "mensajes_" + grupo + ".txt";
            File f = new File(filename);
            if (!f.exists()) { out.println("No hay mensajes en " + grupo); return; }
            try (BufferedReader br = new BufferedReader(new FileReader(f))) {
                String line; int i=0;
                while ((line = br.readLine()) != null) {
                    i++;
                    out.println(i + ") " + line);
                }
            } catch (IOException e) {
                out.println("Error leyendo mensajes.");
            }
        }

        /* ---------- mensajería ---------- */

        private void enviarMensajeGrupo() throws IOException {
            if (invitado) { out.println("Invitados no pueden enviar mensajes."); return; }
            out.println("Escribe mensaje para el grupo '" + grupoActual + "':");
            String msg = in.readLine();
            enviarAMiembrosGrupo(grupoActual, nombre, msg);
            out.println("Mensaje enviado a " + grupoActual);
        }

        private void mensajePrivado() throws IOException {
            out.println("Usuarios conectados: " + clientes.keySet());
            out.println("¿A quién deseas enviar privado?");
            String dest = in.readLine().trim();
            if (!clientes.containsKey(dest)) { out.println("Usuario no conectado."); return; }
            Set<String> bloqueadosPorDest = bloqueos.getOrDefault(dest, Collections.emptySet());
            if (bloqueadosPorDest.contains(nombre)) { out.println("No puedes enviar: te tiene bloqueado."); return; }
            out.println("Escribe mensaje:");
            String msg = in.readLine();
            enviarACliente(dest, "[Privado] " + nombre + ": " + msg);
            out.println("Enviado.");
        }

        /* ---------- bloqueo ---------- */

        private void bloquear() throws IOException {
            out.println("¿A quién deseas bloquear?");
            String u = in.readLine().trim();
            bloqueos.putIfAbsent(nombre, ConcurrentHashMap.newKeySet());
            bloqueos.get(nombre).add(u);
            out.println("Bloqueado: " + u);
        }

        private void desbloquear() throws IOException {
            out.println("Tus bloqueados: " + bloqueos.getOrDefault(nombre, Collections.emptySet()));
            out.println("¿A quién desbloquear?");
            String u = in.readLine().trim();
            Set<String> s = bloqueos.getOrDefault(nombre, ConcurrentHashMap.newKeySet());
            s.remove(u);
            out.println("Desbloqueado: " + u);
        }

        /* ---------- juego del gato ---------- */

        private void proponerGato() throws IOException {
            out.println("Usuarios disponibles: " + clientes.keySet());
            out.println("¿A quién propones jugar?");
            String rival = in.readLine().trim();
            if (!clientes.containsKey(rival)) { out.println("Usuario no conectado."); return; }
            String clave = clavePareja(nombre, rival);
            if (juegosActivos.containsKey(clave)) { out.println("Ya hay una partida entre ustedes."); return; }
            enviarACliente(rival, "Tienes una invitación a jugar de " + nombre + ". Acepta con opción 10 y luego escribe 'ACEPTAR " + nombre + "'");
            out.println("Invitación enviada a " + rival + ". El rival debe aceptar.");
        }

        private void iniciarPartida() throws IOException {
            
        }

        
        private void iniciarJuegoDesdeAccept(String proponente) {
            String rival = nombre;
            String clave = clavePareja(proponente, rival);
            if (juegosActivos.containsKey(clave)) { enviar("Ya existe partida."); return; }
            JuegoGato juego = new JuegoGato(proponente, rival);
            juegosActivos.put(clave, juego);
            String primero = new Random().nextBoolean() ? proponente : rival;
            enviarACliente(proponente, "Partida iniciada contra " + rival + ". Empieza: " + primero);
            enviarACliente(rival, "Partida iniciada contra " + proponente + ". Empieza: " + primero);
            juego.iniciar(primero);
        }

        private String clavePareja(String a, String b) {
            return a.compareTo(b) < 0 ? a + "_" + b : b + "_" + a;
        }

        /* ---------- ranking ---------- */

        private void mostrarRankingGeneral() {
            List<Map.Entry<String,Integer>> list = new ArrayList<>(ranking.entrySet());
            list.sort((a,b) -> b.getValue().compareTo(a.getValue()));
            out.println("=== Ranking ===");
            for (Map.Entry<String,Integer> e : list) out.println(e.getKey() + ": " + e.getValue() + " pts");
        }

        private void compararRanking() throws IOException {
            out.println("Jugador 1:");
            String j1 = in.readLine().trim();
            out.println("Jugador 2:");
            String j2 = in.readLine().trim();
            int p1 = ranking.getOrDefault(j1, 0);
            int p2 = ranking.getOrDefault(j2, 0);
            int total = p1 + p2;
            if (total == 0) out.println("Sin datos entre ambos (0 partidas registradas)");
            else {
                out.println(j1 + ": " + p1 + " pts, " + j2 + ": " + p2 + " pts");
                out.println(String.format("%s %.1f%% - %s %.1f%%", j1, p1*100.0/total, j2, p2*100.0/total));
            }
        }

        /* ---------- archivos remotos (lista / descargar) ---------- */

        private void listarArchivosUsuario() throws IOException {
            out.println("¿De qué usuario listar archivos?");
            String u = in.readLine().trim();
            File dir = new File(".");
            File[] files = dir.listFiles((d,n) -> n.endsWith(".txt") && n.contains(u));
            if (files == null || files.length == 0) { out.println("No hay archivos encontrados."); return; }
            out.println("Archivos:");
            for (int i=0;i<files.length;i++) out.println((i+1) + ") " + files[i].getName());
        }

        private void descargarArchivoUsuario() throws IOException {
            out.println("¿Nombre del archivo a descargar (ej: notas_maria.txt)?");
            String name = in.readLine().trim();
            File f = new File(name);
            if (!f.exists()) { out.println("No existe ese archivo."); return; }
            out.println("INICIO_ARCHIVO");
            try (BufferedReader br = new BufferedReader(new FileReader(f))) {
                String l;
                while ((l = br.readLine()) != null) out.println(l);
            } catch (IOException e) {
                out.println("ERROR leyendo archivo.");
            }
            out.println("FIN_ARCHIVO");
        }
    }

    /* ---------------- JuegoGato ---------------- */

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
            enviarACliente(turno, "Es tu turno. Mueve con: escribir en el menú 10 y elegir 'mover' y la posición 1..9.");
        }


        synchronized String mover(String jugador, int pos) {
            if (finalizada) return "Partida ya finalizada.";
            if (!turno.equals(jugador)) return "No es tu turno.";
            if (pos < 1 || pos > 9) return "Posición fuera de rango.";
            if (board[pos-1] != ' ') return "Casilla ocupada.";
            board[pos-1] = (jugador.equals(a) ? 'X' : 'O');

            String ganador = comprobarGanador();
            if (ganador != null) {
                finalizada = true;
                ranking.put(ganador, ranking.getOrDefault(ganador, 0) + 2);
                String otro = ganador.equals(a) ? b : a;
                
                enviarACliente(a, "FIN: Ganador " + ganador);
                enviarACliente(b, "FIN: Ganador " + ganador);
                
                guardarRanking();
                return null;
            }
            if (tableroLleno()) {
                finalizada = true;
                
                ranking.put(a, ranking.getOrDefault(a, 0) + 1);
                ranking.put(b, ranking.getOrDefault(b, 0) + 1);
                enviarACliente(a, "FIN: Empate");
                enviarACliente(b, "FIN: Empate");
                guardarRanking();
                return null;
            }

            turno = turno.equals(a) ? b : a;
            enviarEstado();
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
                    return (c == 'X') ? a : b;
                }
            }
            return null;
        }

        private void enviarEstado() {
            StringBuilder sb = new StringBuilder();
            sb.append("\nEstado del tablero:\n");
            for (int i=0;i<9;i++) {
                sb.append("[");
                sb.append(board[i] == ' ' ? (i+1) : board[i]);
                sb.append("]");
                if ((i+1)%3==0) sb.append("\n");
            }
            enviarACliente(a, sb.toString());
            enviarACliente(b, sb.toString());
        }
    }
}