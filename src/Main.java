import grafana.sql.ParameterizedQuery;
import grafana.sql.ParameterizedQueryBuilder;
import grafana.sql.QueryMisMatch;

public class Main {
    public static void main(String[] args) throws QueryMisMatch {
        String queryTemplate = "SELECT * FROM table " +
                "WHERE name='Ahmed' and date='${__from:date:YYYY-MM-DD} 00:00:00' and id=$id or " +
                "multi in (\"${servers:sqlstring}\")";
        String query = "SELECT * FROM table " +
                "WHERE name='Ahmed' and date='2019-07-29 00:00:00' and id='0'SELECT * FROM table WHERE id='' or " +
                "multi in ('test''1','test2')";

        ParameterizedQuery parameterizedQuery = ParameterizedQueryBuilder.build(queryTemplate, query);
        System.out.println(parameterizedQuery);
    }
}