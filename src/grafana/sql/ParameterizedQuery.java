package grafana.sql;

import java.util.List;

public class ParameterizedQuery {

    public static final String PREPARED_SQL_PARAM_PLACEHOLDER = "?";

    private String parameterizedSQL;
    private List<List<String>> parameters;
    private List<String> variables;

    public ParameterizedQuery(String parameterizedSQL, List<List<String>> parameters, List<String> variables) {
        this.parameterizedSQL = parameterizedSQL;
        this.parameters = parameters;
        this.variables = variables;
    }

    public String getParameterizedSQL() {
        return parameterizedSQL;
    }

    public void setParameterizedSQL(String parameterizedSQL) {
        this.parameterizedSQL = parameterizedSQL;
    }

    public List<List<String>> getParameters() {
        return parameters;
    }

    public void setParameters(List<List<String>> parameters) {
        this.parameters = parameters;
    }

    public List<String> getVariables() {
        return variables;
    }

    public void setVariables(List<String> variables) {
        this.variables = variables;
    }

    @Override
    public String toString() {
        return "{\n parameterized_query: " + this.parameterizedSQL +
                "\n parameters: " + this.parameters +
                "\n variables: " + this.variables + "\n}";
    }
}
