using System.Text.Json;

namespace BlazorFront.Util
{
    public class JsonUtil
    {
        public static bool IsJson(string input)
        {
            input = input.Trim();
            if ((input.StartsWith("{") && input.EndsWith("}")) || // For object
                (input.StartsWith("[") && input.EndsWith("]")))   // For array
            {
                try
                {
                    JsonDocument.Parse(input);
                    return true;
                }
                catch (JsonException) // Invalid JSON
                {
                    return false;
                }
            }
            else
            {
                return false;
            }
        }
    }
}
