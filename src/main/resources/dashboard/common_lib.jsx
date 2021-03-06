const fetchInterval = 5000

function renderState(groups, subClassName, keyPrefix) {
  return (
    groups.map(groupStatus => renderGroup(groupStatus, subClassName, keyPrefix, ""))
  );
}

function encloseInTable(trs, gid, keyPrefix) {
  return (
    <table key={`${keyPrefix}-group-${gid}-table`}>
      <tbody key={`${keyPrefix}-group-${gid}-tbody`}>
        <tr key={`${keyPrefix}-group-${gid}-tr-header`}>
          <th key={`${keyPrefix}-group-${gid}-th-1`}>Job name</th>
          <th key={`${keyPrefix}-group-${gid}-th-2`}>Data set period</th>
          <th key={`${keyPrefix}-group-${gid}-th-3`}>Last updated</th>
        </tr>
        { trs }
      </tbody>
    </table>
  );
}

function renderGroup(groupStatus, subClassName, keyPrefix, sub) {
  const group = groupStatus.group;
  const gid = group.group_id;
  const jobs = groupStatus["status"].map(jobStatus => (
    renderJob(jobStatus, gid, keyPrefix)))
  return (
    <div key={`${keyPrefix}-group-${gid}`} className={`${subClassName} detail-box`}>
      <h2 key={`${keyPrefix}-group-${gid}-header`}>{group.name}{sub}</h2>
      {encloseInTable(jobs, keyPrefix, gid)}
    </div>);
}

function renderJob(jobStatus, gid, keyPrefix) {
  if(jobStatus["period_health"] != undefined) {
    const job = jobStatus.job;
    const jid = job.job_id;
    const date = new Date(jobStatus.updated_at).toUTCString();
    return(jobStatus.period_health.map((ph, i) => (
      <tr key={`${keyPrefix}-job-${gid}-${jid}-${i}-row`}
          className={(ph.ok == true)?"great":"warn"}>
        <td key={`${keyPrefix}-job-${gid}-${jid}-${i}-job`}>{job.name}</td>
        <td key={`${keyPrefix}-job-${gid}-${jid}-${i}-period`}>{ph.period}</td>
        <td key={`${keyPrefix}-job-${gid}-${jid}-${i}-updated`}>{date}</td>
      </tr>
    )));
  }
}


