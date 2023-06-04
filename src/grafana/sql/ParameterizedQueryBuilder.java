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


    public static ParameterizedQuery build(String queryTemplate, String rawQuery) throws QueryMisMatch {
        // In Grafana, a variable can coexist with a hardcoded parameter (ie: '${__from:date:YYYY-MM-DD} 00:00:00')
        // hence this function templates the whole parameter in order to make valid parameterized query
        queryTemplate = templateParamWGrafanaVariables(queryTemplate);

        String queryUpToVarStart = queryTemplate;
        String rawQueryUpToVarStart = rawQuery;

        // Pattern matcher to get query string before the first grafana variable
        Pattern toVarStart = Pattern.compile("([^\\$]+(?=\"\\$))|([^\\$]+(?='\\$))|([^\\$]+(?=\\$))");
        Matcher matchToVarStart = toVarStart.matcher(queryTemplate);

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

        String queryFromVarStart = queryTemplate.substring(queryUpToVarStart.length());
        String rawQueryFromVarStart = rawQuery.substring(queryUpToVarStart.length());
        queryTemplate = queryFromVarStart;
        rawQuery = rawQueryFromVarStart;
        Pattern varPattern = Pattern.compile(GRAFANA_QUOTED_VAR_REGEX + "|" + GRAFANA_VAR_REGEX);
        Matcher varMatch = varPattern.matcher(queryTemplate);
        List<String> parameters = new ArrayList<>();
        while(varMatch.find()) {
            String currentVar = varMatch.group();
            // Pattern matcher to get query string between grafana current variable and next variable
            matchToVarStart = toVarStart.matcher(queryTemplate.substring(currentVar.length()));

            String matchToLookBehindRawQuery;
            if (matchToVarStart.find()) {
                matchToLookBehindRawQuery = matchToVarStart.group();
            } else {
                // If next variable does not exist get query string after the current variable
                matchToLookBehindRawQuery = queryTemplate.substring(currentVar.length());
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
            for (String v : varValues) {
                String param = unQuoteString(v);
                // This makes sure to add the parameters only if the inputs are safe
                // (I.E numbers || strings || empty)
//                if (isSafeVariableInput(param)) {
//                    preparedStatementPlaceHolders.add(v);
//                } else {
                parameters.add(param);
                preparedStatementPlaceHolders.add(ParameterizedQuery.PREPARED_SQL_PARAM_PLACEHOLDER);
//                }
            }
            preparedQueryBuilder.append(String.join(COMMA_SEPARATOR, preparedStatementPlaceHolders));
            preparedQueryBuilder.append(matchToLookBehindRawQuery);
            // Get template and raw query string from next variable
            queryTemplate = queryTemplate.substring(currentVar.length() + matchToLookBehindRawQuery.length());
            rawQuery = rawQuery.substring(currentVarInput.length() + matchToLookBehindRawQuery.length());

            varMatch = varPattern.matcher(queryTemplate);
        }
        if (!queryTemplate.equals(rawQuery)) {
            throw new QueryMisMatch(QUERY_MISMATCH_EXCEPTION_MESSAGE);
        }
        return new ParameterizedQuery(preparedQueryBuilder.toString(), parameters);
    }

    private static String[] splitByComma(String str) {
        // Using regex to avoid splitting by comma inside quotes
        return str.split("(\\s|\\t)*,(\\s|\\t)*(?=(?:[^'\"]*['|\"][^'\"]*['|\"])*[^'\"]*$)");
    }

    private static String templateParamWGrafanaVariables(String queryTemplate) {
        // TODO: handle escaped quotes and special characters
        Pattern quotedStringPattern = Pattern.compile("(\"(.+?)\")|('(.+?)')");
        Matcher quotedStringMatch = quotedStringPattern.matcher(queryTemplate);
        while(quotedStringMatch.find()) {
            String quotedString = quotedStringMatch.group();
            Matcher varMatcher = Pattern.compile(GRAFANA_VAR_REGEX).matcher(quotedString);
            // If grafana variable exists in single quoted string
            if(varMatcher.find()) {
                String var = varMatcher.group();
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
