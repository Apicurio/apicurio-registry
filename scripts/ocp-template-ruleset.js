const seen = [];
var notRepeated = function(value) {
    if (seen.includes(value)) {
      return [{
        message: `Duplicated value '${value}'`
      }];
    } else {
      seen.push(value);
    }
    return [];
}

module.exports = {
  rules: {
    "parameters-not-repeated": {
      message: "{{error}}",
      given: "$.parameters[*].name",
      severity: "error",
      then: {
        function: notRepeated,
      },
    },
  },
};