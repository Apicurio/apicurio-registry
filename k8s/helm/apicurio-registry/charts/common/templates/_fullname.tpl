{{- /*
fullname defines a suitably unique name for a resource by combining
the release name and the chart name.

The prevailing wisdom is that names should only contain a-z, 0-9 plus dot (.) and dash (-), and should
not exceed 63 characters.

Parameters:

- .Values.fullnameOverride: Replaces the computed name with this given name
- .Values.fullnamePrefix: Prefix
- .Values.global.fullnamePrefix: Global prefix
- .Values.fullnameSuffix: Suffix
- .Values.global.fullnameSuffix: Global suffix

The applied order is: "global prefix + prefix + name + suffix + global suffix"

Usage: 'name: "{{- template "common.fullname" . -}}"'
*/ -}}
{{- define "common.fullname"}}
  {{- $base := default (printf "%s-%s" .Release.Name .Chart.Name) .Values.fullnameOverride -}}
  {{- $gpre := default "" .Values.global.fullnamePrefix -}}
  {{- $pre := default "" .Values.fullnamePrefix -}}
  {{- $suf := default "" .Values.fullnameSuffix -}}
  {{- $gsuf := default "" .Values.global.fullnameSuffix -}}
  {{- $name := print $gpre $pre $base $suf $gsuf -}}
  {{- $name | lower | trunc 54 | trimSuffix "-" -}}
{{- end -}}

{{- /*
common.fullname.unique adds a random suffix to the unique name.

This takes the same parameters as common.fullname

*/ -}}
{{- define "common.fullname.unique" -}}
  {{ template "common.fullname" . }}-{{ randAlphaNum 7 | lower }}
{{- end }}
