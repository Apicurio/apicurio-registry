{{- /*

common.port is a utility for working with port numbers.

A port may be received as either a series of digits or as a port in the
common ":1234" syntax (with a leading ":").

The leading colon, if found, will be stripped. The result will then be cast
to an integer. If the string cannot parse, this will be 0.

It is designed to be friendly to '--set'.

*/ -}}
{{- define "common.port" -}}
  {{- printf "%v" . | replace ":" "" | int -}}
{{- end -}}
{{- define "common.port.string" -}}
  {{- printf "%v" . | replace ":" "" | int | quote -}}
{{- end -}}
