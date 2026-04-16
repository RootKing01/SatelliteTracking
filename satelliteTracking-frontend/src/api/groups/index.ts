import { amateurGroup } from './amateur'
import { beidouGroup } from './beidou'
import { cubesatGroup } from './cubesat'
import { educationGroup } from './education'
import { engineeringGroup } from './engineering'
import { galileoGroup } from './galileo'
import { geoGroup } from './geo'
import { glonassOpsGroup } from './glonassOps'
import { gpsOpsGroup } from './gpsOps'
import { iridiumNextGroup } from './iridiumNext'
import { militaryGroup } from './military'
import { onewebGroup } from './oneweb'
import { planetGroup } from './planet'
import { radarGroup } from './radar'
import { sbasGroup } from './sbas'
import { scienceGroup } from './science'
import { spireGroup } from './spire'
import { starlinkGroup } from './starlink'
import { stationsGroup } from './stations'
import { weatherGroup } from './weather'

export const satelliteGroupSources = [
  stationsGroup,
  starlinkGroup,
  onewebGroup,
  iridiumNextGroup,
  spireGroup,
  gpsOpsGroup,
  galileoGroup,
  glonassOpsGroup,
  beidouGroup,
  sbasGroup,
  scienceGroup,
  weatherGroup,
  planetGroup,
  radarGroup,
  geoGroup,
  amateurGroup,
  cubesatGroup,
  educationGroup,
  engineeringGroup,
  militaryGroup,
] as const
