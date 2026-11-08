import type { AgentProvider } from "./AgentCardViewer";

/**
 * Whether the provider object carries any content worth rendering. The AgentCardEditor
 * treats both fields as optional and can leave a provider as `{}` after a user clears
 * both inputs, so the viewer must treat the empty case as "no provider".
 */
export const hasProviderContent = (provider: AgentProvider | undefined): boolean => {
    if (!provider) return false;
    return !!(provider.organization || provider.url);
};

/**
 * The visible label for the provider link. Prefers the organization name when set so
 * the link reads as "IBM" rather than the raw URL, and falls back to the URL text when
 * only the URL is available. This prevents the previous behavior of rendering an
 * unlabeled external-link icon in the URL-only case (WCAG 2.4.4 Link Purpose).
 */
export const providerLinkText = (provider: AgentProvider): string | undefined => {
    return provider.organization || provider.url;
};
