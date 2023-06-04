import grafana.sql.ParameterizedQuery;
import grafana.sql.ParameterizedQueryBuilder;
import grafana.sql.QueryMisMatch;

public class Main {
    public static void main(String[] args) throws QueryMisMatch {
        String queryTemplate = "SELECT * FROM table WHERE id=$id";
        String query = "SELECT * FROM table WHERE id='0'SELECT * FROM table WHERE id=''";

        ParameterizedQuery parameterizedQuery = ParameterizedQueryBuilder.build(queryTemplate, query);
        System.out.println(parameterizedQuery);

    }
}