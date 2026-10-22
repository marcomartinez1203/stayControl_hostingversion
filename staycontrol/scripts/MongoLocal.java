import com.mongodb.MongoCommandException;
import com.mongodb.client.MongoClients;
import org.bson.Document;
import java.util.List;

/** Herramienta de configuración local. No lee ni escribe colecciones de negocio. */
public class MongoLocal {
    public static void main(String[] args) throws Exception {
        boolean iniciar = args.length == 1 && "--iniciar-rs0".equals(args[0]);
        if (args.length > 0 && !iniciar) { throw new IllegalArgumentException("Usa sin argumentos o --iniciar-rs0."); }
        try (var cliente = MongoClients.create("mongodb://127.0.0.1:27017/?directConnection=true&serverSelectionTimeoutMS=5000")) {
            var admin = cliente.getDatabase("admin");
            var hello = admin.runCommand(new Document("hello", 1));
            System.out.println("MongoDB local: ping=" + admin.runCommand(new Document("ping", 1)).get("ok")
                + ", replicaSet=" + hello.getString("setName") + ", primary=" + hello.getBoolean("isWritablePrimary", false));
            if (!iniciar) { return; }
            if (hello.getString("setName") != null) {
                if (!"rs0".equals(hello.getString("setName"))) { throw new IllegalStateException("Ya existe otro replica set; no se modificó."); }
            } else {
                var opciones = admin.runCommand(new Document("getCmdLineOpts", 1)).get("parsed", Document.class);
                var replicacion = opciones.get("replication", Document.class);
                if (replicacion == null || !"rs0".equals(replicacion.getString("replSetName"))) {
                    throw new IllegalStateException("Configura replication.replSetName: rs0 y reinicia el servicio antes de inicializar.");
                }
                boolean sinInicializar = false;
                try { admin.runCommand(new Document("replSetGetStatus", 1)); }
                catch (MongoCommandException e) { if (e.getErrorCode() != 94) { throw e; } sinInicializar = true; }
                if (!sinInicializar) { throw new IllegalStateException("El replica set ya tiene configuración; no se modificó."); }
                admin.runCommand(new Document("replSetInitiate", new Document("_id", "rs0")
                    .append("members", List.of(new Document("_id", 0).append("host", "localhost:27017")))));
                System.out.println("Replica set rs0 inicializado. No se modificaron datos de negocio.");
            }
            for (int i = 0; i < 30; i++) {
                hello = admin.runCommand(new Document("hello", 1));
                if ("rs0".equals(hello.getString("setName")) && hello.getBoolean("isWritablePrimary", false)) {
                    System.out.println("rs0 PRIMARY: listo para transacciones."); return;
                }
                Thread.sleep(500);
            }
            throw new IllegalStateException("No se confirmó PRIMARY; revisa el servicio antes de iniciar la aplicación.");
        }
    }
}
