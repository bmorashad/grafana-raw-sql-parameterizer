package grafana.sql;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ParameterizedQueryBuilder {

    private static final String QUERY_MISMATCH_EXCEPTION_MESSAGE = "Raw SQL does not match the query template";
    private static final String COMMA_SEPARATOR = ",";
    private static final String VAR_PARAM_TEMPLATE = "$param";
    private static final String GRAFANA_QUOTED_VAR_REGEX = "('\\$(\\d|\\w|_)+')|('\\$\\{.*?\\}')|(\"\\$(\\d|\\w|_)+\")|(\"\\$\\{.*?\\}\")";
    private static final String GRAFANA_VAR_REGEX = "(\\$(\\d|\\w|_)+)|(\\$\\{.*?\\})";
    private static final int GRAFANA_VAR_TYPE1_GROUP = 1;
    private static final int GRAFANA_VAR_TYPE2_GROUP = 3;
    private static final int TOTAL_GRAFANA_VAR_GROUPS = 3;


    private static final String QUOTED_STRING_REGEX = "(\"(.+?)\")|('(.+?)')";
    private static final int DOUBLE_QUOTE_GROUP = 1;
    private static final int SINGLE_QUOTE_GROUP = 3;
    private static final int TOTAL_QUOTED_STRING_GROUPS = 4;


    public static ParameterizedQuery build(String queryTemplate, String rawQuery) throws QueryMisMatch {
        // In Grafana, a variable can coexist with a hardcoded parameter (ie: '${__from:date:YYYY-MM-DD} 00:00:00')
        // hence this function templates the whole parameter in order to make valid parameterized query
        String normalizedQueryTemplate = templateParamWGrafanaVariables(queryTemplate);

        String queryUpToVarStart = normalizedQueryTemplate;
        String rawQueryUpToVarStart = rawQuery;

        // Pattern matcher to get query string before the first grafana variable
        Pattern toVarStart = Pattern.compile("([^\\$]+(?=\"\\$))|([^\\$]+(?='\\$))|([^\\$]+(?=\\$))");
        Matcher matchToVarStart = toVarStart.matcher(normalizedQueryTemplate);

        if (matchToVarStart.find()) {
            queryUpToVarStart = matchToVarStart.group();
            if(rawQuery.length() < queryUpToVarStart.length()) {
                throw new QueryMisMatch(QUERY_MISMATCH_EXCEPTION_MESSAGE);
            }
            rawQueryUpToVarStart = rawQuery.substring(0, queryUpToVarStart.length());
        }
        if(!queryUpToVarStart.equals(rawQueryUpToVarStart)){
            throw new QueryMisMatch(QUERY_MISMATCH_EXCEPTION_MESSAGE);
        }
        StringBuilder preparedQueryBuilder = new StringBuilder().append(queryUpToVarStart);

        String queryFromVarStart = normalizedQueryTemplate.substring(queryUpToVarStart.length());
        String rawQueryFromVarStart = rawQuery.substring(queryUpToVarStart.length());
        normalizedQueryTemplate = queryFromVarStart;
        rawQuery = rawQueryFromVarStart;
        Pattern varPattern = Pattern.compile(GRAFANA_QUOTED_VAR_REGEX + "|" + GRAFANA_VAR_REGEX);
        Matcher varMatch = varPattern.matcher(normalizedQueryTemplate);
        List<List<String>> variableInputs = new ArrayList<>();
        while(varMatch.find()) {
            String currentVar = varMatch.group();
            // Pattern matcher to get query string between grafana current variable and next variable
            matchToVarStart = toVarStart.matcher(normalizedQueryTemplate.substring(currentVar.length()));

            String matchToLookBehindRawQuery;
            if (matchToVarStart.find()) {
                matchToLookBehindRawQuery = matchToVarStart.group();
            } else {
                // If next variable does not exist get query string after the current variable
                matchToLookBehindRawQuery = normalizedQueryTemplate.substring(currentVar.length());
            }
            String currentVarInput;
            if (matchToLookBehindRawQuery.isEmpty()) {
                // If there is no string after the current variable, then remaining segment of rawQuery is the
                // current variable input (rawQuery is sliced up to the next variable)
                currentVarInput = rawQuery;
            } else {
                Matcher lookBehindRQ = Pattern.compile("(.*?)(?=" + Pattern.quote(matchToLookBehindRawQuery) + ")").matcher(rawQuery);
                if (!lookBehindRQ.find()) {
                    throw new QueryMisMatch(QUERY_MISMATCH_EXCEPTION_MESSAGE);
                }
                currentVarInput = lookBehindRQ.group();
            }
            // Grafana variable input can be multivalued, which are separated by comma by default
            String[] varValues = splitByComma(currentVarInput);
            List<String> preparedStatementPlaceHolders = new ArrayList<>();
            // Group the inputs of a variable together in a List
            // This allows to map the inputs with variables correctly
            List<String> variableInputGroup = new ArrayList<>();
            for (String v : varValues) {
                String param = unQuoteString(v);
                // This makes sure to add the variableInputs only if the inputs are safe
                // (I.E numbers || strings || empty)
//                if (isSafeVariableInput(param)) {
//                    preparedStatementPlaceHolders.add(v);
//                } else {
                variableInputGroup.add(param);
                preparedStatementPlaceHolders.add(ParameterizedQuery.PREPARED_SQL_PARAM_PLACEHOLDER);
//                }
            }
            variableInputs.add(variableInputGroup);
            preparedQueryBuilder.append(String.join(COMMA_SEPARATOR, preparedStatementPlaceHolders));
            preparedQueryBuilder.append(matchToLookBehindRawQuery);
            // Get template and raw query string from next variable
            normalizedQueryTemplate = normalizedQueryTemplate.substring(currentVar.length() + matchToLookBehindRawQuery.length());
            rawQuery = rawQuery.substring(currentVarInput.length() + matchToLookBehindRawQuery.length());

            varMatch = varPattern.matcher(normalizedQueryTemplate);
        }
        if (!normalizedQueryTemplate.equals(rawQuery)) {
            throw new QueryMisMatch(QUERY_MISMATCH_EXCEPTION_MESSAGE);
        }
        List<String> templateVariables = extractGrafanaVariables(queryTemplate);
        return new ParameterizedQuery(preparedQueryBuilder.toString(), variableInputs, templateVariables);
    }

    private static List<String> extractGrafanaVariables(String queryTemplate) {
        Pattern varPattern = Pattern.compile(QUOTED_STRING_REGEX + "|" + GRAFANA_VAR_REGEX);
        Matcher varMatch = varPattern.matcher(queryTemplate);
        List<String> grafanaVariables = new ArrayList<>();
        while (varMatch.find()) {
            String doubleQuoteVar = varMatch.group(DOUBLE_QUOTE_GROUP);
            if (doubleQuoteVar != null) {
                Matcher grafanaVarMatcher = Pattern.compile(GRAFANA_VAR_REGEX).matcher(doubleQuoteVar);
                if (grafanaVarMatcher.find()) {
                    grafanaVariables.add(doubleQuoteVar);
                }
                continue;
            }
            String singleQuoteVar = varMatch.group(SINGLE_QUOTE_GROUP);
            if (singleQuoteVar != null) {
                Matcher grafanaVarMatcher = Pattern.compile(GRAFANA_VAR_REGEX).matcher(singleQuoteVar);
                if (grafanaVarMatcher.find()) {
                    grafanaVariables.add(singleQuoteVar);
                }
                continue;
            }
            String unquoteVarType1 = varMatch.group(TOTAL_QUOTED_STRING_GROUPS + GRAFANA_VAR_TYPE1_GROUP);
            if (unquoteVarType1 != null) {
                grafanaVariables.add(unquoteVarType1);
                continue;
            }
            String unquoteVarType2 = varMatch.group(TOTAL_QUOTED_STRING_GROUPS + GRAFANA_VAR_TYPE2_GROUP);
            if (unquoteVarType2 != null) {
                grafanaVariables.add(unquoteVarType2);
            }
        }
        return grafanaVariables;
    }

    private static String[] splitByComma(String str) {
        // Using regex to avoid splitting by comma inside quotes
        return str.split("(\\s|\\t)*,(\\s|\\t)*(?=(?:[^'\"]*['|\"][^'\"]*['|\"])*[^'\"]*$)");
    }

    private static String templateParamWGrafanaVariables(String queryTemplate) {
        // TODO: handle escaped quotes and special characters
        Pattern quotedStringPattern = Pattern.compile(QUOTED_STRING_REGEX);
        Matcher quotedStringMatch = quotedStringPattern.matcher(queryTemplate);
        while(quotedStringMatch.find()) {
            String quotedString = quotedStringMatch.group();
            Matcher varMatcher = Pattern.compile(GRAFANA_VAR_REGEX).matcher(quotedString);
            // If grafana variable exists in single quoted string
            if(varMatcher.find()) {
                String templatedQuotedString = templateQuotedString(quotedString);
                // escape any special characters
                templatedQuotedString = Matcher.quoteReplacement(templatedQuotedString);
                queryTemplate = queryTemplate.replaceFirst(Pattern.quote(quotedString), templatedQuotedString);
            }
        }
        return queryTemplate;
    }

    private static String templateQuotedString(String quotedString) {
        return quotedString.replaceFirst("[^']+|[^\"]+",
                Matcher.quoteReplacement(VAR_PARAM_TEMPLATE));
    }

    private static boolean isSafeVariableInput(String currentVarInput) {
        if (currentVarInput == null || currentVarInput.isEmpty()) {
            return true;
        }
        return currentVarInput.matches("\\$?[a-zA-Z0-9-_\\.]+|^\"[a-zA-Z0-9-_\\.\\s]+\"$|^'[a-zA-Z0-9-_\\.\\s]+'$");
    }

    private static String unQuoteString(String str) {
        if (isQuoted(str)) {
            int firstCharIndex = 0;
            int lastCharIndex = str.length() - 1;
            return str.substring(firstCharIndex + 1, lastCharIndex);
        }
        return str;
    }

    private static boolean isQuoted(String str) {
        if (str == null || str.isEmpty()) {
            return false;
        }
        int firstCharIndex = 0;
        int lastCharIndex = str.length() - 1;
        return (str.charAt(firstCharIndex) == '\'' && str.charAt(lastCharIndex) == '\'') ||
                (str.charAt(firstCharIndex) == '"' && str.charAt(lastCharIndex) == '"');
    }
}
