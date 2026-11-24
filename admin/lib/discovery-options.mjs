const maxResults = 40;
const domainPattern = /^(?=.{1,253}$)(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\.)+[a-z]{2,63}$/;
const datePattern = /^\d{4}-\d{2}-\d{2}$/;
const dateValid = value => datePattern.test(value) && Number.isFinite(Date.parse(value)) &&
  new Date(value).toISOString().slice(0, 10) === value;
const firecrawlDate = date => [date.slice(5, 7), date.slice(8, 10), date.slice(0, 4)].join('/');

export function readDiscoveryOptions({ query = '', from = '', to = '', minUrls = '1', domains = '' } = {}) {
  query = query.trim();
  if (query.length > 240 || /[\u0000-\u001f\u007f]/.test(query)) throw Error('invalid-search-query');
  if ((from || to) && (!dateValid(from) || !dateValid(to) || from > to)) throw Error('invalid-date-range');
  const minimum = Number(minUrls || '1');
  if (!/^\d{1,2}$/.test(minUrls || '1') || minimum < 1 || minimum > maxResults)
    throw Error('invalid-minimum-source-urls');
  const hosts = [...new Set(domains.trim().toLowerCase().split(/[,\s]+/).filter(Boolean))];
  if (hosts.length > 5 || hosts.some(host => !domainPattern.test(host))) throw Error('invalid-source-domains');
  return { query, from, to, minimum, hosts,
    tbs: from ? 'cdr:1,cd_min:' + firecrawlDate(from) + ',cd_max:' + firecrawlDate(to) : 'sbd:1' };
}
