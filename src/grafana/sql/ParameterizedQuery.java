package grafana.sql;

import java.util.List;

public class ParameterizedQuery {

    public static final String PREPARED_SQL_PARAM_PLACEHOLDER = "?";

    private String parameterizedSQL;
    private List<List<String>> varInputs;
    private List<String> variables;

    public ParameterizedQuery(String parameterizedSQL, List<List<String>> varInputs, List<String> variables) {
        this.parameterizedSQL = parameterizedSQL;
        this.varInputs = varInputs;
        this.variables = variables;
    }

    public String getParameterizedSQL() {
        return parameterizedSQL;
    }

    public void setParameterizedSQL(String parameterizedSQL) {
        this.parameterizedSQL = parameterizedSQL;
    }

    public List<List<String>> getVarInputs() {
        return varInputs;
    }

    public void setVarInputs(List<List<String>> varInputs) {
        this.varInputs = varInputs;
    }

    public List<String> getVariables() {
        return variables;
    }

    public void setVariables(List<String> variables) {
        this.variables = variables;
    }

    @Override
    public String toString() {
        return "{\n parameterized_sql: " + this.parameterizedSQL +
                "\n variable_inputs: " + this.varInputs +
                "\n variables: " + this.variables + "\n}";
    }
}
