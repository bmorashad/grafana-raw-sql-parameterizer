package grafana.sql;

import java.util.List;

public class ParameterizedQuery {

    public static final String PREPARED_SQL_PARAM_PLACEHOLDER = "?";

    private String paramaterizedSQL;
    private List<String> parameters;

    public ParameterizedQuery(String paramaterizedSQL, List<String> parameters) {
        this.paramaterizedSQL = paramaterizedSQL;
        this.parameters = parameters;
    }

    public String getParameterizedSQL() {
        return paramaterizedSQL;
    }

    public void setParameterizedSQL(String paramaterizedSQL) {
        this.paramaterizedSQL = paramaterizedSQL;
    }

    public List<String> getParameters() {
        return parameters;
    }

    public void setParameters(List<String> parameters) {
        this.parameters = parameters;
    }

    @Override
    public String toString() {
        return "{\n parameterized_query: " + this.paramaterizedSQL +
                "\n parameters: " + this.parameters + "\n}";
    }
}
