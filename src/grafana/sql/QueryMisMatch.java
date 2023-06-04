package grafana.sql;

public class QueryMisMatch extends Exception {

    private static final long serialVersionUID = -3171277334737076294L;

    public QueryMisMatch(String msg) {
        super(msg);
    }
}
